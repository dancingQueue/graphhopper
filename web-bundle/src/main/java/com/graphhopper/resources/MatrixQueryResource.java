/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.resources;

import com.graphhopper.*;
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.omg.SendingContext.RunTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Peter Karich
 */
@Path("matrix")
public class MatrixQueryResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopperAPI graphHopper;
    private final Boolean hasElevation;

    @Inject
    public MatrixQueryResource(GraphHopperAPI graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @Context ContainerRequestContext rc,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("source_point") List<GHPoint> sourceRequestPoints,
            @QueryParam("destination_point") List<GHPoint> destinationRequestPoints,
            @QueryParam("type") @DefaultValue("json") String type,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("weighting") @DefaultValue("fastest") String weighting,
            @QueryParam("algorithm") @DefaultValue("") String algoStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.Routing.POINT_HINT) List<String> pointHints,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("heading") List<Double> favoredHeadings,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString) {
        boolean writeGPX = "gpx".equalsIgnoreCase(type);
        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        if (sourceRequestPoints.isEmpty())
            throw new IllegalArgumentException("You have to pass at least one point");
        if (enableElevation && !hasElevation)
            throw new IllegalArgumentException("Elevation not supported!");
        if (favoredHeadings.size() > 1 && favoredHeadings.size() != sourceRequestPoints.size())
            throw new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                    + "or equal to the number of points (" + sourceRequestPoints.size() + ")");
        if (pointHints.size() > 0 && pointHints.size() != sourceRequestPoints.size())
            throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored");


//        int sourcePointsLength = sourceRequestPoints.size();
//        int destinationPointsLength = destinationRequestPoints.size();
//        List<GHRequest> requests = new ArrayList<>();
//        for (int i = 0; i < sourcePointsLength; i++) {
//            for (int j = 0; j < destinationPointsLength; j++) {
//                GHRequest request = new GHRequest(sourceRequestPoints.get(i), destinationRequestPoints.get(j));
//                request.setVehicle(vehicleStr).
//                        setWeighting(weighting).
//                        setAlgorithm(algoStr).
//                        setLocale(localeStr).
//                        setPointHints(pointHints).
//                        setPathDetails(pathDetails).
//                        getHints().
//                        put(CALC_POINTS, calcPoints).
//                        put(INSTRUCTIONS, instructions).
//                        put(WAY_POINT_MAX_DISTANCE, minPathPrecision);
//
//                requests.add(request);
//            }
//        }

        GHRequest request = new GHRequest(sourceRequestPoints.get(0), destinationRequestPoints.get(0));
        initHints(request.getHints(), uriInfo.getQueryParameters());
        request.setVehicle(vehicleStr).
                setWeighting(weighting).
                setAlgorithm(algoStr).
                setLocale(localeStr).
                setPointHints(pointHints).
                setPathDetails(pathDetails).
                getHints().
                put(CALC_POINTS, calcPoints).
                put(INSTRUCTIONS, instructions).
                put(WAY_POINT_MAX_DISTANCE, minPathPrecision);


        LowLevelMatrixResponse lowLevelMatrixResponse = new LowLevelMatrixResponse();
        GHResponse ghResponse = new GHResponse();
        graphHopper.calcPathsForMatrix(request, sourceRequestPoints, destinationRequestPoints, lowLevelMatrixResponse, ghResponse);

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr + " " + sourceRequestPoints + ", took:"
                + took + ", " + algoStr + ", " + weighting + ", " + vehicleStr;

        if (ghResponse.hasErrors()) {
            throw new RuntimeException();
        } else {
            logger.info(logStr + ", Matrix is calculated");
            return Response.ok(WebHelper.jsonObject(lowLevelMatrixResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }

    private static Response.ResponseBuilder gpxSuccessResponseBuilder(GHResponse ghRsp, String timeString, String
            trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints, String version) {
        if (ghRsp.getAll().size() > 1) {
            throw new IllegalArgumentException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        return Response.ok(ghRsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints, version), "application/gpx+xml").
                header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx");
    }

    static void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.put(e.getKey(), e.getValue().get(0));
            } else {
                // Do nothing.
                // TODO: this is dangerous: I can only silently swallow
                // the forbidden multiparameter. If I comment-in the line below,
                // I get an exception, because "point" regularly occurs
                // multiple times.
                // I think either unknown parameters (hints) should be allowed
                // to be multiparameters, too, or we shouldn't use them for
                // known parameters either, _or_ known parameters
                // must be filtered before they come to this code point,
                // _or_ we stop passing unknown parameters alltogether..
                //
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
            }
        }
    }

}
