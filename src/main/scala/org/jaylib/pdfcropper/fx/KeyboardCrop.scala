package org.jaylib.pdfcropper.fx
import org.jaylib.pdfcropper.CropBox
import org.jaylib.pdfcropper.Utils.function2EventHandler

import javafx.scene.input.KeyCode.{ A, C, DIGIT2, DIGIT3, DOWN, END, EQUALS, H, HOME, L, LEFT, MINUS }
import javafx.scene.input.KeyCode.{ PAGE_DOWN, PAGE_UP, PLUS, R, RIGHT, SPACE, UP, Z }
import javafx.scene.input.KeyEvent
import javafx.stage.FileChooser
import javafx.stage.Stage

class KeyboardCrop(
    protected val logic: CropLogicFx,
    protected val stage: Stage,
    protected val fc: FileChooser) {
  private var dist = 1
  private def d = dist
  private var lastKey = (SPACE -> true)

  private val keyHandler = ((event: KeyEvent) => {
    val newKey = (event.getCode -> event.isShiftDown)
    // if the same key is hit again and again, the new distance the crop is shifted
    // will be doubled - maximum is 8.
    if (lastKey == newKey)
      dist = 8.min(d*2)
    else {
      dist = 1
      lastKey = newKey
    }

    def adjustCrop(x0: Int, y0: Int, x1: Int, y1: Int) = {
      for (page <- 0 to 1; if logic.isActiveEditor(page))
        logic.adjustCrop(page, CropBox(x0, y0, x1, y1))
    }
    val c = logic.cropBox(if (logic.isActiveEditor(0)) 0 else 1) 
    newKey match {
      // keys to set the CropBox
      case (RIGHT, false)     => adjustCrop(-d, 0, -d, 0) // > >
      case (RIGHT, true)      => adjustCrop(-d, 0, d, 0)  // < >
      case (LEFT, false)      => adjustCrop(d, 0, d, 0)   // < <
      case (LEFT, true)       => adjustCrop(d, 0, -d, 0)  // > <
      case (DOWN, false)      => adjustCrop(0, d, 0, d)  
      case (DOWN, true)       => adjustCrop(0, -d, 0, d)
      case (UP, false)        => adjustCrop(0, -d, 0, -d)
      case (UP, true)         => adjustCrop(0, d, 0, -d)
      case (PLUS, false)      => adjustCrop(-d, -d, d, d)
      case (EQUALS, false)    => adjustCrop(-d, -d, d, d)
      case (MINUS, false)     => adjustCrop(d, d, -d, -d)
      // keys to select the page
      case (SPACE, false)     => logic.swapActiveEdit
      case (PAGE_UP, false)   => logic.incPage(-1)
      case (PAGE_DOWN, false) => logic.incPage(1)
      case (HOME, false)      => logic.firstPage
      case (END, false)       => logic.lastPage
      case (H, false)         => logic.initPage
      // auto crop
      case (A, false)         => logic.autoCrop(1)
      case (A, true)          => logic.autoCrop() // using intermediate of several pages
      // shortcuts for buttons
      case (L, false)         => logic.file = fc.showOpenDialog(stage)
      case (R, false)         => logic.reload
      case (C, false)         => logic.exec(logic.exportFile(1))
      case (DIGIT2, false)    => logic.exec(logic.exportFile(2))
      case (DIGIT3, false)    => logic.exec(logic.exportFile(3))
      // undo
      case (Z, false)         => logic.revertCrop
      case _                  => println("unsupported key: " + newKey)
    }
  })
  logic.buttons.foreach(_.setOnKeyReleased(keyHandler))

  stage.addEventFilter(KeyEvent.ANY, (event: KeyEvent) => {
    if (!event.isShiftDown && !event.isControlDown && (event.getCode == RIGHT || event.getCode == LEFT || event.getCode == UP || event.getCode == DOWN || event.getCode == SPACE)) {
      if (event.getEventType == KeyEvent.KEY_RELEASED) {
        keyHandler(event)
      }
      event.consume
    }
  })

}