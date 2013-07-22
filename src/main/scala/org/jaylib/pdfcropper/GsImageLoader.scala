package org.jaylib.pdfcropper

import java.awt.image.BufferedImage
import java.io.File
import scala.sys.process._
import scala.util.Try
import javax.imageio.ImageIO
import Utils.usingTempFile
import java.io.IOException

object GsImageLoader {

  private def loadImage(pdf: File, page: Int, sizeCmd: Seq[String], cropSettings: CropSettings) = {
    if (!pdf.exists)
      throw new IllegalArgumentException(pdf.getAbsolutePath() + " does not exist!")
   
    usingTempFile(File.createTempFile("img", ".png")) { temp =>
      (Seq(cropSettings.settings.ghostscript, "-q",
        f"-o${temp.getAbsolutePath()}", "-sDEVICE=png256",
        f"-dFirstPage=${page}", f"-dLastPage=${page}") ++
        sizeCmd ++
        Seq(f"-f${pdf.getAbsolutePath}")).! // blocking execute the gs-tool: create a png using the crop-box, show only one page
      ImageIO.read(temp.toURI().toURL())
    }
  }
  /**
   * Uses ghostscript to create a png image of a PDF for a given page number
   *
   * @param pdf: the PDF input file
   * @param page: the page number to print
   * @return the page as an image
   */
  def load(pdf: File, page: Int, cropSettings: CropSettings): BufferedImage = loadImage(pdf, page, Seq("-dUseCropBox"), cropSettings)

  /**
   * Uses ghostscript to create a png image of a PDF for a given page number
   *
   * @param pdf: the PDF input file
   * @param page: the page number to print
   * @return the page as an image
   */
  def load(pdf: File, page: Int, crop: CropBox, cropSettings: CropSettings): BufferedImage = loadImage(pdf, page,
    Seq(f"-g${crop.width}x${crop.height}",
      "-c", f"<</Install {${crop.width - crop.x1} ${crop.height - crop.y1} translate}>> setpagedevice"), cropSettings)

  class AlgorithmSettings(val limit: Double = 0.01, val chkSize: Int = 7, val widenBy: Int = 1)

  def findAutoBox(img: BufferedImage, settings: AlgorithmSettings = new AlgorithmSettings): CropBox = {
    val width = img.getWidth(null);
    val height = img.getHeight(null);

    def calcAverage(pixels: Array[Int]): Double =
      pixels.foldLeft(0.0)((avg, pixel) =>
        avg + (1.0 -
          (((pixel >> 16) & 0xff) + ((pixel >> 8) & 0xff) + (pixel & 0xff)) / (3.0 * 0xff))) / pixels.length

    val pixelRows = img.getRGB(0, 0, width, height, null, 0, width)
    val avgLines = pixelRows.grouped(width).map(calcAverage).toList

    def pixelColumns(i: Int): Stream[Int] = pixelRows(i) #:: pixelColumns(i + width)
    val avgColumns = List.range(0, width).map(i => calcAverage(pixelColumns(i).take(height).toArray)).toList

    @scala.annotation.tailrec
    def findFirst(it: Iterator[Seq[Double]], index: Int = 0): Int = {
      if (it.hasNext) {
        if (it.next.forall(_ > settings.limit))
          index - 1
        else
          findFirst(it, index + 1)
      }
      else
        0
    }

    val x0 = findFirst(avgColumns.sliding(settings.chkSize))
    // y is upside down in an image buffer (compared to CropBox-coordinates in PDF)
    val y1 = height - findFirst(avgLines.sliding(settings.chkSize))
    val x1 = width - findFirst(avgColumns.reverse.sliding(settings.chkSize))
    val y0 = findFirst(avgLines.reverse.sliding(settings.chkSize))

    val w = settings.widenBy
    CropBox(x0 - w, y0 - w, x1 + w, y1 + w)
  }

}