package org.jaylib.pdfcropper.fx
import org.jaylib.pdfcropper.CropBox
import org.jaylib.pdfcropper.Utils.function2EventHandler
import javafx.animation.Animation.INDEFINITE
import javafx.animation.FadeTransitionBuilder
import javafx.geometry.Point2D
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.{LineTo, MoveTo, Path, Rectangle}
import javafx.util.Duration

class VisualCrop(
    protected val logic: CropLogicFx,
    protected val borderWitdh: Int) extends Group {

  val rect = new Rectangle(0, 0, 0, 0) { setArcHeight(5); setArcWidth(5); setFill(Color.DODGERBLUE) }
  val pathX, pathY = new Path { setStrokeWidth(1); setStroke(Color.BLACK) }
  val imvBox = logic.imvBox
  
  getChildren.addAll(imvBox, rect)

  FadeTransitionBuilder.create.duration(Duration.seconds(4)).node(rect).fromValue(0.3).toValue(0.1).cycleCount(INDEFINITE).autoReverse(true).build.play

  private var anchorPt = new Point2D(0, 0);

  onMouseMovedProperty.set((event: MouseEvent) => {
    pathY.getElements.setAll(new MoveTo(borderWitdh, event.getY), new LineTo(imvBox.getImageWidth+2*borderWitdh, event.getY))
    if (event.getX < imvBox.getImageWidth+4*borderWitdh)
    	pathX.getElements.setAll(new MoveTo(event.getX, borderWitdh), new LineTo(event.getX, imvBox.getImageHeight))
  })
  onMousePressedProperty.set((event: MouseEvent) => {
    pathX.getElements.clear
    pathY.getElements.clear
    // initialize rectangle
    rect.setX(event.getX)
    rect.setY(event.getY);
    // start point of rectangle
    anchorPt = new Point2D(event.getX, event.getY)
  })
  // dragging draws the CropBox as a rectangle
  onMouseDraggedProperty.set((event: MouseEvent) => {
    rect.setWidth((event.getX - anchorPt.getX).abs)
    rect.setHeight((event.getY - anchorPt.getY).abs)
    if (event.getX < anchorPt.getX)
      rect.setX(event.getX)
    if (event.getY < anchorPt.getY)
      rect.setY(event.getY)
  })
  // fix the rectangle and call the crop function
  onMouseReleasedProperty.set((event: MouseEvent) => {
    updateCrop
  })
  


  def updateCrop {
    if (rect.getHeight > 10 || rect.getWidth > 10) {
      val width = imvBox.getImageWidth
      val height = imvBox.getImageHeight
      rect.setX((rect.getX - imvBox.borderWidth).max(0))
      rect.setY((rect.getY - imvBox.borderWidth).max(0))
      rect.setWidth(rect.getWidth.min(width))
      rect.setHeight(rect.getHeight.min(height))
      val bufferRegion = 20

      def cropFromRect(index: Int, x: Double, y: Double, w: Double, h: Double) = {
        val c = logic.cropBox(index)
        // y coordinates are turned upside down from select to CropBox
        CropBox(c.x0 + x.toInt, (c.y0 + height - y - h).toInt, (c.x0 + x + w).toInt, (c.y0 + height - y).toInt)
      }

      def checkCut(idx: Int): Option[CropBox] = {
        if ((rect.getX + rect.getWidth) < (imvBox(idx).getWidth / 3))
          // cut left third
          Some(cropFromRect(idx, rect.getX + rect.getWidth, 0, imvBox(idx).getWidth - rect.getX - rect.getWidth, imvBox(idx).getHeight))

        else {
          if (rect.getX > (imvBox(idx).getWidth * 2 / 3))
            // cut right third 
            Some(cropFromRect(idx, 0, 0, rect.getX, imvBox(idx).getHeight))

          else {
            if ((rect.getY + rect.getHeight) < (imvBox(idx).getHeight / 3))
              // cut upper third
              Some(cropFromRect(idx, 0, rect.getY + rect.getHeight, imvBox(idx).getWidth, imvBox(idx).getHeight - rect.getY - rect.getHeight))

            else {
              if (rect.getY > (imvBox(idx).getHeight * 2 / 3))
                // cut lower third
                Some(cropFromRect(idx, 0, 0, imvBox(idx).getWidth, rect.getY))
              else
                None
            }
          }
        }
      }
      def getLeftCropBox = {
        checkCut(0) match {
          case Some(c) => c
          case _       => cropFromRect(0, rect.getX, rect.getY, rect.getWidth, rect.getHeight)
        }
      }
      def getRightCropBox = {
        rect.setX(rect.getX - imvBox(0).getWidth - 2 * imvBox.borderWidth)
        val crop = checkCut(1) match {
          case Some(c) => c
          case _       => cropFromRect(1, rect.getX, rect.getY, rect.getWidth, rect.getHeight)
        }
        rect.setX(rect.getX + imvBox(0).getWidth + 2 * imvBox.borderWidth)
        crop
      }
      if (logic.doublePages) {
        // are we on the left or right side or is it a crop spanning both pages?
        if ((rect.getX < imvBox(0).getWidth) && ((rect.getX + rect.getWidth) < (imvBox(0).getWidth + bufferRegion))) {
          // crop or cut on the left page left
          rect.setWidth(Math.min(imvBox(0).getWidth, rect.getWidth))
          logic.setCropBox(0,getLeftCropBox)
        }
        else {
          if (rect.getX > (imvBox(0).getWidth - bufferRegion)) {
            // we are on the right page
            rect.setWidth(Math.min(imvBox(1).getWidth, rect.getWidth))
            logic.setCropBox(1,getRightCropBox)
          }
          else {
            // it's a crop that spans over both pages
            val origWidth = rect.getWidth
            rect.setWidth(imvBox(0).getWidth - rect.getX) // work on the left part
            val cropLeft = getLeftCropBox
            rect.setX(rect.getX + rect.getWidth) // is equal to previous invBox.boxes(0).getWidth = left side width
            rect.setWidth(origWidth - rect.getWidth)
            logic.setCropBox(1,getRightCropBox)
            logic.setCropBox(0,cropLeft)
          }
        }
      }
      else logic.setCropBox(0,getLeftCropBox)
    }
    rect.setX(0)
    rect.setY(0)
    rect.setWidth(0)
    rect.setHeight(0)
  }

}