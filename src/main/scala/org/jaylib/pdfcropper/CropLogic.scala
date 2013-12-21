package org.jaylib.pdfcropper

import java.awt.image.BufferedImage
import java.io.File
import Utils.usingTempFile
import akka.actor.Props
import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill

/** Contains the settings and general actions of the Cropper.
 */

trait CropSettings {
  val settings = SettingsAsUserPrefs()
  def doublePages: Boolean = { settings.twoPages }
  def doublePages_=(two: Boolean): Unit
  def isActiveEditor(index: Int) = ((settings.activeEditor & (1 << index)) != 0)
  def initialDir = settings.initDir
}

abstract class CropLogic extends CropSettings {

  /** The current PDF file */
  private[this] var currentFile = new File(".")
  /** Total number of pages in the PDF file */
  protected var numPages = 1
  /** The currently selected page number */
  private[this] var selectedPageNo = 1
  /** the currently set CropBox */
  private[this] val currentCropBoxes = Array(CropBox(0, 0, 300, 300), CropBox(0, 0, 300, 300))
  /** save the old value when in undo action (one undo step) */
  private[this] val oldCrop = Array(CropBox(0, 0, 300, 300), CropBox(0, 0, 300, 300))

  private def setFile(newFile: File) {
    currentFile = newFile
    settings.initDir = file.getParent
    numPages = EditPdf.getPageNumbers(file)
    if (numPages < 2)
      doublePages = false
    selectedPageNo = getInitPage
    try {
      currentCropBoxes(0) = EditPdf.getCropBox(file, pageNo)
      if (settings.twoPages) {
        currentCropBoxes(1) = EditPdf.getCropBox(file, pageNo + 1)
      }
    }
    catch {
      // the document does not contain a cropbox - use the document's size instead
      case _: Throwable =>
        val img = GsImageLoader.load(currentFile, pageNo, this)
        currentCropBoxes(0) = CropBox(0, 0, img.getWidth, img.getHeight)
        if (settings.twoPages) {
          val img = GsImageLoader.load(currentFile, pageNo + 1, this)
          currentCropBoxes(1) = CropBox(0, 0, img.getWidth, img.getHeight)
        }
    }
    updateImage(0)
    if (settings.twoPages) {
      updateImage(1)
    }
  }
  def file = currentFile
  /** Sets the current file - used when loading or reloading a file.
   */
  def file_=(newFile: File) { setFile(newFile) }

  def reload = { setFile(currentFile) }

  def pageNo = selectedPageNo
  protected def pageNo_=(newPageNo: Int) {
    selectedPageNo = newPageNo
    updateImage(0)
    if (settings.twoPages)
      updateImage(1)
  }

  /** Crops the current file.
   *  @param splitNum: if 1 => no splitting done, >1: each page is split into splitNum parts
   */
  def exportFile(splitNum: Int) = EditPdf.export(file, if (settings.twoPages) currentCropBoxes.toList.reverse else List(currentCropBoxes(0)), settings.leaveCover,
    new EditPdf.SplitSettings(parts = splitNum, buffer = settings.pagesBuffer, rotate = if (splitNum == 1) 0 else settings.rotateSplitPages))

  /** Crops the current page. Uses only the y-settings.
   *  @param splitNum: if 1 => no splitting done, >1: each page is split into splitNum parts
   */
  private[this] def upateCropBoxY(index: Int, cropBox: CropBox) {
    val copy = currentCropBoxes(index)
    currentCropBoxes(index) = CropBox(copy.x0, cropBox.y0, copy.x1, cropBox.y1)
  }
  /** Updates the CropBox.
   */
  def cropBox(index: Int) = currentCropBoxes(index)
  def setCropBox(index: Int, newCropBox: CropBox) = {
    oldCrop(index) = currentCropBoxes(index)
    currentCropBoxes(index) = newCropBox
    if (settings.twoPages && settings.sameHeight && currentCropBoxes(0).height != currentCropBoxes(1).height) {
      val otherIndex = if (index == 0) 1 else 0
      upateCropBoxY(otherIndex, newCropBox)
      updateImage(otherIndex)
    }
    updateImage(index)
  }

  def adjustCrop(index: Int, relativeCrop: CropBox) {
    oldCrop(index) = currentCropBoxes(index)
    currentCropBoxes(index) += relativeCrop
    onShiftImage(relativeCrop, index)
    if (settings.twoPages && settings.sameHeight && currentCropBoxes(0).height != currentCropBoxes(1).height) {
      val otherIndex = if (index == 0) 1 else 0
      currentCropBoxes(otherIndex) += CropBox(0, relativeCrop.y0, 0, relativeCrop.y1)
      updateImage(otherIndex)
    }
    updateImage(index)
  }

  override def doublePages_=(two: Boolean) = {
    settings.twoPages = two
    if (two) {
      currentCropBoxes(1) = currentCropBoxes(0)
    }
    else {
      onClearImage(1)
    }
  }

  def setCropBoxes(newCropBoxes: Array[CropBox]) = {
    val eq = Array(currentCropBoxes(0) == oldCrop(0), currentCropBoxes(1) == oldCrop(1))
    currentCropBoxes.copyToArray(oldCrop)
    newCropBoxes.copyToArray(currentCropBoxes)
    if (settings.sameHeight && currentCropBoxes(0).height != currentCropBoxes(1).height) {
      // same height option is set -> use the minimum height for both crop boxes
      if (currentCropBoxes(0).height < currentCropBoxes(1).height) {
        upateCropBoxY(0, currentCropBoxes(1))
        eq(0) = false
      }
      else {
        upateCropBoxY(1, currentCropBoxes(0))
        eq(1) = false
      }
    }
    for (i <- 0 to 1) if (!eq(i)) updateImage(i)
  }
  /** Reverts the cropBox to the previous value (undo).
   */
  def revertCrop {
    currentCropBoxes(0) = oldCrop(0)
    updateImage(0)
    if (settings.twoPages) {
      currentCropBoxes(1) = oldCrop(1)
      updateImage(1)
    }
  }

  def incPage(inc: Int) {
    pageNo = pageNo + { if (settings.twoPages) 2 * inc else inc }
  }
  def firstPage { pageNo = 1 }
  def lastPage { pageNo = numPages }
  protected def getInitPage = (((numPages + 2) / 4) * 2 + 1) // half of the document, left page is ensured to be odd
  def initPage { pageNo = getInitPage }

  def autoCrop(chkPages: Int = settings.autoPagesNumber) {
    val chk = if (chkPages < 2 && settings.twoPages) 2 else chkPages
    val first = (pageNo - (chk - 1) / 2).max(1)
    val last = (first + chk).min(numPages)
    val count = last - first + 1
    // calculate the crops for several pages using in parallel - using the "par"
    val crops = for (page <- (first to last).par; cropShift = cropBox((page + 1) & 1))
      yield GsImageLoader.findAutoBox(GsImageLoader.load(file, page, cropShift, this)) + cropShift

    // set the new crop box using the minimum/maximum values of the collected crops
    if (!crops.isEmpty) {
      if (settings.twoPages) {
          // build streams of crops - the first containing the odd (first, third, etc.) elements - the second the even
          def part(i: Int): Stream[CropBox] = crops(i) #:: part(i + 2)
        val boxes = Array(
          part(0).take((count + 1) / 2),
          part(1).take(count / 2)).map(crops2 =>
            CropBox(crops2.map(_.x0).min, crops2.map(_.y0).min, crops2.map(_.x1).max, crops2.map(_.y1).max))

        setCropBoxes(if ((first & 1) == 1) boxes else boxes.reverse)
        updateImages
      }
      else {
        setCropBox(0, CropBox(crops.map(_.x0).min, crops.map(_.y0).min, crops.map(_.x1).max, crops.map(_.y1).max))
      }
    }
  }

  protected def updateImage(index: Int) {
    import CropLogic._

    master ! LoadImage(file, pageNo + index, currentCropBoxes(index), this, index)
  }

  def updateImages() {
    updateImage(0)
    if (settings.twoPages) {
      updateImage(1)
    }
  }

  def exec(f: File) {
    import scala.sys.process._
    if (settings.callExec)
      ("cmd /c \"" + f.getAbsolutePath + "\"").run
  }

  protected def onUpdateImage(image: BufferedImage, index: Int)
  protected def onClearImage(index: Int)
  protected def onShiftImage(relativeCrop: CropBox, index: Int)

  def configOptions()
}

object CropLogic {
  val system = ActorSystem("updateImage")
  val master = system.actorOf(Props[Master], "master")
  val updaters = Array(system.actorOf(Props[ImageUpdater], "updater0"), system.actorOf(Props[ImageUpdater], "updater1"))

  case class LoadImage(pdf: File, page: Int, crop: CropBox, cropLogic: CropLogic, index: Int)
  case class FinishedLoading(cropLogic: CropLogic, image: BufferedImage, index: Int)
  
  def onExit : Unit = {
    system.shutdown
  }

  class Master extends Actor {
    private var loading: Boolean = false
    private var count = 0
    def receive = {
      case loadImage: LoadImage =>
        if (loading) {
            // kill the old and create a new updater
            updaters(loadImage.index) ! PoisonPill
            updaters(loadImage.index) = system.actorOf(Props[ImageUpdater], s"updater${count}_${loadImage.index}")   
            count += 1
        }
        updaters(loadImage.index) ! loadImage
        loading = true
      case FinishedLoading(cropLogic, image, index) =>
        if (sender == updaters(index)) {
        	cropLogic.onUpdateImage(image, index)
        	loading = false
        }
    }
  }
  class ImageUpdater extends Actor {
    def receive = {
      case LoadImage(pdf, page, crop, cropLogic, index) =>
        master ! FinishedLoading(cropLogic, GsImageLoader.load(pdf, page, crop, cropLogic), index)
    }
  }

}
