/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2018-2020 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 */

/*
 * This Groovy script uses the orbit API to segment an image.
 * Error handling is omitted to ease the reading of the script but
 * this should be added
 * if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

import java.awt.Shape

import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation

import com.actelion.research.orbit.imageAnalysis.components.RecognitionFrame
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.models.RectangleExt
import com.actelion.research.orbit.imageAnalysis.models.SegmentationResult
import com.actelion.research.orbit.imageAnalysis.utils.OrbitHelper
import com.actelion.research.orbit.imageprovider.ImageProviderOmero
import com.actelion.research.orbit.imageprovider.OmeroConf


import omero.model.ImageI
import omero.model.PolygonI
import omero.model.RoiI

import omero.gateway.Gateway
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import static omero.rtypes.rstring
import static omero.rtypes.rint


// Edit these parameters
String username = "trainer-1"
String password = "password"
String hostname = "wss://outreach.openmicroscopy.org/omero-ws"
int omeroImageId = 10001

// Login to create a new connection with OMERO
OmeroConf conf = new OmeroConf(hostName, 443, true)
conf.setUseWebSockets(true)
ImageProviderOmero imageProvider = new ImageProviderOmero(conf)
imageProvider.authenticateUser(username, password)
DALConfig.setImageProvider(imageProvider)
Gateway gateway = imageProvider.getGatewayAndCtx().getGateway()
SecurityContext ctx = imageProvider.getGatewayAndCtx().getCtx()

//Load the image
browse = gateway.getFacility(BrowseFacility)
image = browse.getImage(ctx, omeroImageId)


// Load Models that I own. OMERO annotations of type: Model
imageProvider.setOnlyOwnerObjects(true)
List<RawAnnotation> annotations = imageProvider.LoadRawAnnotationsByType(RawAnnotation.ANNOTATION_TYPE_MODEL)
println("Found " + annotations.size() + " files")
// Use the most recent annotations
int fileAnnId = 0
for (RawAnnotation ra : annotations) {
    id = ra.getRawAnnotationId()
    if (id > fileAnnId) {
        fileAnnId = id
    }
 }

OrbitModel model = OrbitModel.LoadFromOrbit(fileAnnId)
println("Loaded Model: " + model.getName())

// Select a 500x500 pixels region in centre of the image
pixels = image.getDefaultPixels()
w = 250
h = 250
cx = (int) (pixels.getSizeX()/2)
cy = (int) (pixels.getSizeY()/2)
region = new RectangleExt(cx-w, cy-w, 2*w, 2*h)

// Perform the segmentation
RawDataFile rdf = imageProvider.LoadRawDataFile(omeroImageId)
RecognitionFrame rf = new RecognitionFrame(rdf, false)
SegmentationResult res = OrbitHelper.Segmentation(rf, omeroImageId, model, null, 1, true, region)


// handle the segmented objects
println("SegmentationResult: " + res.shapeList.size() + " shapes")
List<RoiI> roisToSave = new ArrayList<RoiI>()
for (Shape shape: res.shapeList) {
    // can cast shape to Polygon or simply listPoints
    String points = shape.listPoints()

    // Create Polygon in OMERO
    p = new PolygonI()
    // Convert "x, y; x, y" format to "x, y, x, y" for OMERO
    points = points.replace(";", ",")
    p.setPoints(rstring(points))
    p.setTheT(rint(0))
    p.setTheZ(rint(0))
    p.setStrokeColor(rint(-65281))   // yellow

    // Add each shape to an ROI on the Image
    ImageI image = new ImageI(omeroImageId, false)
    RoiI roi = new RoiI()
    roi.setImage(image)
    roi.addShape(p)
    roisToSave.add(roi)
}

gateway.getUpdateService(ctx).saveAndReturnArray(roisToSave)

println("Close...")
imageProvider.close()
