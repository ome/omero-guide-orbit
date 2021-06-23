/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2018-2020 University of Dundee. All rights reserved.
 *
 *
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 * 
 *  Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, 
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
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
OmeroConf conf = new OmeroConf(hostname, 443, true)
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
