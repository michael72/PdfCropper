package org.jaylib.pdfcropper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.JavaConversions._
import scala.util.Try

import com.itextpdf.text.{ Document }
import com.itextpdf.text.pdf.{ PdfReader, PdfRectangle, PdfStamper }
import com.itextpdf.text.pdf.{ PdfArray, PdfCopy, PdfNumber }
import com.itextpdf.text.pdf.PdfName.{ CROPBOX, ROTATE }

object EditPdf {

  class SplitSettings (
      val parts: Int,
      val buffer: Int,
      val rotate: Int
    )
  /**
   * Crops a PDF-file given the CropBox to another PDF file.
   * Depending on `numParts` splits it into multiple parts.
   *
   * @param file: the input PDF file
   * @param cropBox: the region of the PDF pages that shall be cropped
   * @param numParts: the number of parts each page is split to (vertically) - if 1: no split is done
   * @param addMargin: in case the document is split, this is an additional between the split parts added to each side,
   * so that text-lines that are split in half probably will be whole on the first page or on the second page of the split.
   * @return the file handle to the cropped PDF file
   */
  def export(file: File, cropBoxes: List[CropBox], leaveCoverPage: Boolean = false, split : SplitSettings = new SplitSettings(1,0,0)): File = {

    val path = file.getAbsolutePath()
    val prefix = path.substring(0, path.lastIndexOf('.'))
    val orig = new PdfReader(new BufferedInputStream(new FileInputStream(file)));
    val dest = new File(prefix + "_crop" + { if (split.parts > 1) { split.parts } else { "" } } + ".pdf")
    val tempCopy = File.createTempFile("copy", ".pdf")
    val offset = if (leaveCoverPage) 2 else 1
    val angle = if (split.rotate != 0) Some(new PdfNumber(split.rotate)) else None

    // in case the document shall be split in several parts, the document pages are each multiplied
    // and the resulting rectangles are split in attached boxes
    val (reader, rects) = if (split.parts > 1) {
      val numPages = orig.getNumberOfPages
      val document = new Document
      // copy the document and add each page twice (or thrice, depending on numParts)
      val copy = new PdfCopy(document, new FileOutputStream(tempCopy))
      document.open
      if (leaveCoverPage)
        copy.addPage(copy.getImportedPage(orig, 1))
      for (page <- offset to numPages; importedPage = copy.getImportedPage(orig, page); i <- 1 to split.parts)
        copy.addPage(importedPage)
      copy.close

      val parts = cropBoxes.map(_.split(parts = split.parts, buffer = split.buffer).toList).flatten
      // for each part p: calculate the crop region
      val rects = parts.map(p => new PdfRectangle(p.x0, p.y0, p.x1, p.y1)).toArray

      // the result: the multiplied document and the crop-regions for each page
      (new PdfReader(new BufferedInputStream(new FileInputStream(tempCopy))), rects)
    }
    else (orig, cropBoxes.map(box => new PdfRectangle(box.x0, box.y0, box.x1, box.y1)).toArray)
    
    val idxOffset = if (split.parts == 1) 0 else offset
    for (i <- (offset to reader.getNumberOfPages()).par) {
      val pageDict = reader.getPageN(i)
      pageDict.put(CROPBOX, rects((i+idxOffset) % rects.length))
      if (angle != None) // the half (or third) split pages are rotated by 270
        pageDict.put(ROTATE, angle.get)
    }
    // write the cropped (and split and rotated) document
    new PdfStamper(reader, new FileOutputStream(dest)).close

    // delete the previous temporary file
    if (tempCopy.exists())
      tempCopy.delete

    dest
  }

  def PdfReader(file: File) = new PdfReader(new BufferedInputStream(new FileInputStream(file)))

  /**
   * Gets the number of pages of the given PDF document.
   *
   * @param file the PDF document to check
   * @return the number of pages in the PDF document
   */
  def getPageNumbers(file: File): Int = PdfReader(file).getNumberOfPages

  /**
   * Extracts some pages from a PDF file
   *
   * @param pdf the input PDF-file
   * @param pageStart the starting page number to extract
   * @param pageEnd the last page number to extract
   *    * @param the temporary PDF-file containing a single page of the input PDF-file.
   * The caller should delete the file, when it is not needed any more.
   */
  def extractPages_(pdf: File, pageStart: Int, pageEnd: Int): File = {
    val reader = new PdfReader(new BufferedInputStream(new FileInputStream(pdf)));
    reader.selectPages(s"${pageStart}-${pageEnd}")
    val temp = File.createTempFile("extract", ".pdf")
    val document = new Document
    val copy = new PdfCopy(document, new FileOutputStream(temp));
    document.open();
    for (i <- 1 to pageEnd - pageStart + 1)
      copy.addPage(copy.getImportedPage(reader, i));
    document.close();

    temp.deleteOnExit()
    temp
  }

  /**
   * Tries to get the CropBox defined in the PDF.
   *
   * @param file: the PDF file
   * @param pageNo: the optional page number
   * @return the contained CropBox parameters as CropBox
   */
  def getCropBox(file: File, pageNo: Int): CropBox = {
    // this is not straightforward, as getPageN contains a map of PdfObject
    // but item CROPBOX is of type PdfArray containing PdfNumber as PdfObject (hence twice asInstanceOf)
    CropBox(PdfReader(file).getPageN(pageNo).get(CROPBOX).asInstanceOf[PdfArray].iterator()
      .toArray.map(cropVal => cropVal.asInstanceOf[PdfNumber].intValue))
  }

}