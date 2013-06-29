package org.jaylib.pdfcropper

import java.awt.image.BufferedImage
import java.io.File
import Utils.usingTempFile
import org.jaylib.pdfcropper.UserPrefsImplicits._

/**
 * Contains the settings and general actions of the Cropper.
 */

trait CropSettings extends SettingsAsUserPrefs {
  def doublePages: Boolean = { twoPages.value }
  def doublePages_=(two: Boolean): Unit
  def isActiveEditor(index: Int) = ((activeEditor.value & (1 << index)) != 0)
  def initialDir = initDir
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
    initDir.value = file.getParent
    numPages = EditPdf.getPageNumbers(file)
    if (numPages < 2)
      doublePages = false
    selectedPageNo = getInitPage
    try {
      currentCropBoxes(0) = EditPdf.getCropBox(file, pageNo)
      if (twoPages) {
        currentCropBoxes(1) = EditPdf.getCropBox(file, pageNo + 1)
      }
    }
    catch {
      // the document does not contain a cropbox - use the document's size instead
      case _: Throwable =>
        val img = GsImageLoader.load(currentFile, pageNo)
        currentCropBoxes(0) = CropBox(0, 0, img.getWidth, img.getHeight)
        if (twoPages) {
          val img = GsImageLoader.load(currentFile, pageNo + 1)
          currentCropBoxes(1) = CropBox(0, 0, img.getWidth, img.getHeight)
        }
    }
    updateImage(0)
    if (twoPages) {
      updateImage(1)
    }
  }
  def file = currentFile
  /**
   * Sets the current file - used when loading or reloading a file.
   */
  def file_=(newFile: File) { setFile(newFile) }

  def reload = { setFile(currentFile) }

  def pageNo = selectedPageNo
  protected def pageNo_=(newPageNo: Int) {
    selectedPageNo = newPageNo
    updateImage(0)
    if (twoPages)
      updateImage(1)
  }

  /**
   * Crops the current file.
   * @param splitNum: if 1 => no splitting done, >1: each page is split into splitNum parts
   */
  def exportFile(splitNum: Int) = EditPdf.export(file, if (twoPages) currentCropBoxes.toList.reverse else List(currentCropBoxes(0)), leaveCover,
    new EditPdf.SplitSettings(parts = splitNum, buffer = pagesBuffer, rotate = if (splitNum == 1) 0 else rotateSplitPages))

  /**
   * Crops the current page.
   * @param splitNum: if 1 => no splitting done, >1: each page is split into splitNum parts
   */
  private[this] def upateCropBoxY(index: Int, cropBox: CropBox) {
    val copy = currentCropBoxes(index)
    currentCropBoxes(index) = CropBox(copy.x0, cropBox.y0, copy.x1, cropBox.y1)
  }
  /**
   * Updates the CropBox.
   */
  def cropBox(index: Int) = currentCropBoxes(index)
  def setCropBox(index: Int, newCropBox: CropBox) = {
    oldCrop(index) = currentCropBoxes(index)
    currentCropBoxes(index) = newCropBox
    if (twoPages && sameHeight && currentCropBoxes(0).height != currentCropBoxes(1).height) {
      val otherIndex = if (index == 0) 1 else 0
      upateCropBoxY(otherIndex, newCropBox)
      updateImage(otherIndex)
    }
    updateImage(index)
  }

  override def doublePages_=(two: Boolean) = {
    twoPages.value = two
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
    if (sameHeight && currentCropBoxes(0).height != currentCropBoxes(1).height) {
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
  /**
   * Reverts the cropBox to the previous value (undo).
   */
  def revertCrop {
    currentCropBoxes(0) = oldCrop(0)
    updateImage(0)
    if (twoPages) {
      currentCropBoxes(1) = oldCrop(1)
      updateImage(1)
    }
  }

  def incPage(inc: Int) {
    pageNo = pageNo + { if (twoPages) 2 * inc else inc }
  }
  def firstPage { pageNo = 1 }
  def lastPage { pageNo = numPages }
  protected def getInitPage = (((numPages + 2) / 4) * 2 + 1) // half of the document, left page is ensured to be odd
  def initPage { pageNo = getInitPage }

  def autoCrop(chkPages: Int = autoPagesNumber) {
    val chk = if (chkPages < 2 && twoPages) 2 else chkPages
    val first = (pageNo - (chk - 1) / 2).max(1)
    val last = (first + chk).min(numPages)
    val count = last - first + 1
    // calculate the crops for several pages using in parallel - using the "par"
    val crops = for (page <- (first to last).par; cropOrig = cropBox((page + 1) & 1))
      yield GsImageLoader.findAutoBox(GsImageLoader.load(file, page, cropOrig)).shiftBy(cropOrig)

    // set the new crop box using the minimum/maximum values of the collected crops
    if (!crops.isEmpty) {
      if (twoPages) {
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
    onUpdateImage(GsImageLoader.load(file, pageNo + index, currentCropBoxes(index)), index)
  }

  def updateImages() {
    onUpdateImage(GsImageLoader.load(file, pageNo, cropBox(0)), 0)
    if (twoPages) {
      onUpdateImage(GsImageLoader.load(file, pageNo + 1, cropBox(1)), 1)
    }
  }

  def exec(f: File) {
    import scala.sys.process._
    if (callExec)
      ("cmd /c \"" + f.getAbsolutePath + "\"").run
  }

  protected def onUpdateImage(image: BufferedImage, index: Int)
  protected def onClearImage(index: Int)

  def configOptions()
}

