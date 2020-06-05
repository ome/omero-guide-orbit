import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.models.SegmentationResult
import com.actelion.research.orbit.imageAnalysis.utils.OrbitHelper

import java.awt.Shape

import com.actelion.research.orbit.imageprovider.ImageProviderOmero
import com.actelion.research.orbit.imageprovider.OmeroConf


import omero.model.ImageI
import omero.model.PolygonI
import omero.model.RoiI

import omero.gateway.Gateway
import omero.gateway.SecurityContext
import static omero.rtypes.rstring
import static omero.rtypes.rint
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility

// Edit these parameters
String username = "trainer-1"
String password = "password"
String hostname = "workshop.openmicroscopy.org"

// Use the currently opened image...
final OrbitImageAnalysis OIA = OrbitImageAnalysis.getInstance()
ImageFrame iFrame = OIA.getIFrame()
println("selected image: " + iFrame)
RawDataFile rdf = iFrame.rdf

// Get the OMERO Image ID
long omeroImageId = rdf.getRawDataFileId()
println("ID:" + omeroImageId)

// Login to create a new connection with OMERO
ImageProviderOmero imageProvider = new ImageProviderOmero(new OmeroConf(hostName, 4064, true))
imageProvider.authenticateUser(username, password)
Gateway gateway = imageProvider.getGatewayAndCtx().getGateway()
SecurityContext ctx = imageProvider.getGatewayAndCtx().getCtx()

// Load Models that I own. OMERO annotations of type: Model
imageProvider.setOnlyOwnerObjects(true)
List<RawAnnotation> annotations = imageProvider.LoadRawAnnotationsByType(RawAnnotation.ANNOTATION_TYPE_MODEL)
println("Found " + annotations.size() + " files")
// Use the first annotation
int fileAnnId = 0
for (RawAnnotation ra : annotations) {
    id = ra.getRawAnnotationId()
    if (id > fileAnnId) {
        fileAnnId = id
    }
 }

OrbitModel model = OrbitModel.LoadFromOrbit(fileAnnId)
println("Loaded Model: " + model.getName())

// Perform the segmentation
SegmentationResult res = OrbitHelper.Segmentation(rdf.rawDataFileId, model, null, 1)

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
