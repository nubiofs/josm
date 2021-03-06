// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Utils;

/**
 * Read content from an Overpass server.
 *
 * @since 8744
 */
public class OverpassDownloadReader extends BoundingBoxDownloader {

    final String overpassServer;
    final String overpassQuery;

    /**
     * Constructs a new {@code OverpassDownloadReader}.
     *
     * @param downloadArea   The area to download
     * @param overpassServer The Overpass server to use
     * @param overpassQuery  The Overpass query
     */
    public OverpassDownloadReader(Bounds downloadArea, String overpassServer, String overpassQuery) {
        super(downloadArea);
        this.overpassServer = overpassServer;
        this.overpassQuery = overpassQuery.trim();
    }

    @Override
    protected String getBaseUrl() {
        return overpassServer;
    }

    @Override
    protected String getRequestForBbox(double lon1, double lat1, double lon2, double lat2) {
        if (overpassQuery.isEmpty())
            return super.getRequestForBbox(lon1, lat1, lon2, lat2);
        else {
            String realQuery = completeOverpassQuery(overpassQuery);
            return "interpreter?data=" + Utils.encodeUrl(realQuery)
                    + "&bbox=" + lon1 + ',' + lat1 + ',' + lon2 + ',' + lat2;
        }
    }

    private static String completeOverpassQuery(String query) {
        int firstColon = query.indexOf(';');
        if (firstColon == -1) {
            return "[bbox];" + query;
        }
        int bboxPos = query.indexOf("[bbox");
        if (bboxPos > -1 && bboxPos < firstColon) {
            return query;
        }

        int bracketCount = 0;
        int pos = 0;
        for (; pos < firstColon; ++pos) {
            if (query.charAt(pos) == '[')
                ++bracketCount;
            else if (query.charAt(pos) == ']')
                --bracketCount;
            else if (bracketCount == 0) {
                if (!Character.isWhitespace(query.charAt(pos)))
                    break;
            }
        }

        if (pos < firstColon) {
            // We start with a statement, not with declarations
            return "[bbox];" + query;
        }

        // We start with declarations. Add just one more declaration in this case.
        return "[bbox]" + query;
    }

    @Override
    protected InputStream getInputStreamRaw(String urlStr, ProgressMonitor progressMonitor, String reason,
                                            boolean uncompressAccordingToContentDisposition) throws OsmTransferException {
        try {
            return super.getInputStreamRaw(urlStr, progressMonitor, reason, uncompressAccordingToContentDisposition);
        } catch (OsmApiException ex) {
            final String errorIndicator = "Error</strong>: ";
            if (ex.getMessage() != null && ex.getMessage().contains(errorIndicator)) {
                final String errorPlusRest = ex.getMessage().split(errorIndicator)[1];
                if (errorPlusRest != null) {
                    final String error = errorPlusRest.split("</")[0];
                    ex.setErrorHeader(error);
                }
            }
            throw ex;
        }
    }

    @Override
    protected void adaptRequest(HttpClient request) {
        // see https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL#timeout
        final Matcher timeoutMatcher = Pattern.compile("\\[timeout:(\\d+)\\]").matcher(overpassQuery);
        final int timeout;
        if (timeoutMatcher.find()) {
            timeout = 1000 * Integer.parseInt(timeoutMatcher.group(1));
        } else {
            timeout = 180_000;
        }
        request.setConnectTimeout(timeout);
        request.setReadTimeout(timeout);
    }

    @Override
    protected String getTaskName() {
        return tr("Contacting Server...");
    }

    @Override
    protected DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        return new OsmReader() {
            @Override
            protected void parseUnknown(boolean printWarning) throws XMLStreamException {
                if ("remark".equals(parser.getLocalName())) {
                    if (parser.getEventType() == XMLStreamConstants.START_ELEMENT) {
                        final String text = parser.getElementText();
                        if (text.contains("runtime error")) {
                            throw new XMLStreamException(text);
                        }
                    }
                }
                super.parseUnknown(printWarning);
            }
        }.doParseDataSet(source, progressMonitor);
    }

    @Override
    public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {

        DataSet ds = super.parseOsm(progressMonitor);

        // add bounds if necessary (note that Overpass API does not return bounds in the response XML)
        if (ds != null && ds.dataSources.isEmpty()) {
            if (crosses180th) {
                Bounds bounds = new Bounds(lat1, lon1, lat2, 180.0);
                DataSource src = new DataSource(bounds, getBaseUrl());
                ds.dataSources.add(src);

                bounds = new Bounds(lat1, -180.0, lat2, lon2);
                src = new DataSource(bounds, getBaseUrl());
                ds.dataSources.add(src);
            } else {
                Bounds bounds = new Bounds(lat1, lon1, lat2, lon2);
                DataSource src = new DataSource(bounds, getBaseUrl());
                ds.dataSources.add(src);
            }
        }

        return ds;
    }
}
