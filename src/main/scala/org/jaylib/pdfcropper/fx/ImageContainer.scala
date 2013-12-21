package org.jaylib.pdfcropper.fx

import javafx.scene.layout.VBox
import javafx.scene.layout.HBox
import javafx.scene.image.ImageView
import java.awt.image.BufferedImage
import javafx.embed.swing.SwingFXUtils

class ImageContainer extends HBox {
  val imvs = Array(new ImageView, new ImageView)
  val boxes = Array(new VBox, new VBox)
  val borderWidth = 5
  private var twoPagesGetter: (Unit => Boolean) = null
  def init(twoPages: (Unit => Boolean)) {
    twoPagesGetter = twoPages
    imvs.foreach { imv =>
      imv.setSmooth(true)
      imv.setPreserveRatio(true)
    }
  }

  val maxSize = java.awt.Toolkit.getDefaultToolkit.getScreenSize
  val HEIGHT_OFFSET = 50
  def getImageHeight: Int = {
    val heights: Array[Double] = imvs.map { imv =>
      if (imv != null && imv.getImage != null)
        imv.getImage.getHeight
      else 0
    }
    val ret = if (twoPagesGetter())
      heights.max
    else heights(0)
    if (ret > maxSize.getHeight - HEIGHT_OFFSET) {
      imvs.foreach { imv =>
        if (imv != null) {
          imv.setFitHeight(maxSize.getHeight - HEIGHT_OFFSET)
        }
      }
      (maxSize.getHeight - HEIGHT_OFFSET).toInt
    } else ret.toInt
  }

  def getImageWidth: Int = {
    if (imvs == null)
      0
    else {
      val widths = imvs.map(imv => if (imv != null && imv.getImage != null) imv.getImage.getWidth else 0)
      (if (twoPagesGetter())
        widths.sum
      else widths(0)).toInt
    }
  }

  def apply(idx: Int) = imvs(idx).getImage

  boxes(0).getChildren.add(imvs(0))
  boxes(1).getChildren.add(imvs(1))
  getChildren.addAll(boxes(0), boxes(1))

  def updateBorderStyle(twoPages: Boolean, activeEditor: Int) {
    if ((activeEditor & 1) != 0)
      boxes(0).setStyle(s"-fx-border-color: #A0C8FF; -fx-border-width: ${borderWidth};")
    else
      boxes(0).setStyle(s"-fx-border-color: #C0C0C0; -fx-border-width: ${borderWidth};")
    if (twoPages) {
      if ((activeEditor & 2) != 0)
        boxes(1).setStyle(s"-fx-border-color: #A0C8FF; -fx-border-width: ${borderWidth};")
      else
        boxes(1).setStyle(s"-fx-border-color: #C0C0C0; -fx-border-width: ${borderWidth};")
    } else {
      boxes(1).setStyle(s"-fx-border-color: #FFFFFF; -fx-border-width: 0;")
    }
  }

  def getPreferredWidth = {
    getImageWidth + { if (twoPagesGetter()) 40 else 30 }
  }

}
