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
 * This Groovy script uses the orbit API to save the Orbit rois back to OMERO.
 * Error handling is omitted to ease the reading of the script but
 * this should be added
 * if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */


import java.awt.Point
import java.awt.Polygon
import java.awt.Shape
import java.util.ArrayList
import java.util.List

import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation
import com.actelion.research.orbit.imageAnalysis.components.ImageFrame
import com.actelion.research.orbit.imageAnalysis.components.OrbitImageAnalysis
import com.actelion.research.orbit.imageAnalysis.models.ImageAnnotation
import com.actelion.research.orbit.imageAnalysis.models.IScaleableShape
import com.actelion.research.orbit.imageprovider.ImageProviderOmero
import com.actelion.research.orbit.imageprovider.OmeroConf


import omero.gateway.Gateway
import omero.gateway.SecurityContext
import omero.model.ImageI
import omero.model.RoiI
import omero.model.PolygonI

import static omero.rtypes.rstring
import static omero.rtypes.rint

// Example script to show how to load Orbit ROI annotations from OMERO
// and convert them to Polygons on the Image.

// Edit these parameters
String username = "trainer-1"
String password = "password"
String hostname = "wss://workshop.openmicroscopy.org/omero-ws"

// Use the currently opened image...
final OrbitImageAnalysis OIA = OrbitImageAnalysis.getInstance()
ImageFrame iFrame = OIA.getIFrame()
println("selected image: " + iFrame)
RawDataFile rdf = iFrame.rdf

// Get the OMERO Image ID
int omeroImageId = rdf.getRawDataFileId()
println("ID:" + omeroImageId)

// Login to create a new connection with OMERO
OmeroConf conf = new OmeroConf(hostname, 443, true)
conf.setUseWebSockets(true)
ImageProviderOmero imageProvider = new ImageProviderOmero(conf)
imageProvider.authenticateUser(username, password)
Gateway gateway = imageProvider.getGatewayAndCtx().getGateway()
SecurityContext ctx = imageProvider.getGatewayAndCtx().getCtx()

// Load all annotations on the OMERO Image
List<RawAnnotation> annotations = imageProvider.LoadRawAnnotationsByRawDataFile(omeroImageId)
println("Found " + annotations.size() + " files")

List<RoiI> roisToSave = new ArrayList<RoiI>()
for (RawAnnotation ann: annotations) {
	// Cast to ImageAnnotation, scale to 100 and get Points
	ImageAnnotation ia = new ImageAnnotation(ann)
	Polygon poly = ((IScaleableShape) ia.getFirstShape()).getScaledInstance(100d, new Point(0, 0))
	String points = poly.listPoints()
	println(points)

	//Create Polygon in OMERO
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
// Save
gateway.getUpdateService(ctx).saveAndReturnArray(roisToSave)

println("Close...")
imageProvider.close()
