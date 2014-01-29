package org.jaylib.pdfcropper

/**
 * Helper class to define a PDF-CropBox as a rectangle.
 * @param x0: left x-coordinate
 * @param y0: top y coordinate
 * @param x1: right x-coordinate
 * @param y0: lower y coordinate
 */
case class CropBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
  def width = x1 - x0
  def height = y1 - y0

  /**
   * Splits a CropBox into multiple parts along the y-axis.
   */
  def split(parts: Int, buffer: Int): Array[CropBox] = {
    for (i <- 0 until parts)
      yield new CropBox(
      x0, y0 + (i * (y1 - y0) / parts) - { if (i > 0) (buffer + 1) / 2 else 0 },
      x1, y0 + ((i + 1) * (y1 - y0) / parts) + { if (i < parts - 1) buffer / 2 else 0 })
  }.reverse.toArray

  override def toString = (x0, y0, x1, y1).toString
  
  def + (cropBoxAdd: CropBox) = CropBox(
    x0 + cropBoxAdd.x0, y0 + cropBoxAdd.y0,
    x1 + cropBoxAdd.x0, y1 + cropBoxAdd.y0)
}

object CropBox {
  def apply(arr: Array[Int]) = new CropBox(arr(0), arr(1), arr(2), arr(3))
}