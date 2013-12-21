package org.jaylib.pdfcropper.fx
import java.io.File
import org.jaylib.pdfcropper.Utils.function2EventHandler
import javafx.application.Application
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.{ Menu, MenuBar, MenuItem, SeparatorMenuItem }
import javafx.scene.layout.{ HBox, VBox }
import javafx.stage.{ FileChooser, Stage }
import javafx.stage.FileChooser.ExtensionFilter
import org.jaylib.pdfcropper.CropLogic

class PdfCropperFx extends Application {

  override def start(stage: Stage) {
    stage.setTitle("Pdf Cropper")

    val fc = new FileChooser
    fc.setTitle("Open PDF File")
    fc.getExtensionFilters.add(new ExtensionFilter("PDF document", "*.pdf"))

    val imgContainer = new ImageContainer
    val logic = new CropLogicFx(stage, fc, imgContainer)
    val keyboardCrop = new KeyboardCrop(logic, stage, fc)

    val buttonPane = new VBox(5) {
      getChildren.addAll(new VisualCrop(logic, imgContainer.borderWidth), new HBox(5) {
        setPadding(new Insets(5))
        for (button <- logic.buttons)
          getChildren().add(button)
      })
    }
    
    val menuBar = new MenuBar {
      def createMenuItem(action: => Any, txt: String = "") = new MenuItem {
        setText(txt)
        setOnAction((_: ActionEvent) => action)
      }
      def onExit {
        stage.close
        CropLogic.onExit
      }
      getMenus.addAll(
        new Menu(
          "File") {
          getItems.addAll(
            createMenuItem(logic.file = fc.showOpenDialog(stage),
              "Load"),
            createMenuItem(logic.reload,
              "Reload"),
            new SeparatorMenuItem,
            createMenuItem(logic.exec(logic.exportFile(1)),
              "Export to File"),
            createMenuItem(logic.exec(logic.exportFile(2)),
              "Split & Export to File"),
            new SeparatorMenuItem,
            createMenuItem(onExit,
              "Exit"))
        },
        new Menu(
          "Edit") {
          getItems.addAll(
            createMenuItem(logic.revertCrop,
              "Undo"),
            new SeparatorMenuItem,
            createMenuItem(logic.autoCrop(1),
              "Auto Crop Page"),
            createMenuItem(logic.autoCrop(),
              "Auto Crop x Pages"),
            new SeparatorMenuItem,
            createMenuItem(logic.configOptions,
              "Options"))
        },
        new Menu(
          "Help") {
          getItems.addAll()
        })

    }

    stage.setScene(new Scene(new VBox {
      getChildren.addAll(menuBar, buttonPane)
    }, 300, 300))

    val newFile = PdfCropperFx.initialPdf match {
      case Some(file) if new File(file).exists => new File(file)
      case _ =>
        val initDir = logic.initialDir
        if (!initDir.isEmpty) {
          val f = new File(initDir)
          if (f.exists() && f.isDirectory())
        	  fc.setInitialDirectory(new File(initDir))
        }
        try {
          fc.showOpenDialog(stage)
        }
        catch {
          case e: Exception =>
            fc.setInitialDirectory(new File(System.getProperty("user.home")))
            fc.showOpenDialog(stage)
        }
    }

    if (newFile != null) {
      logic.file = newFile
      stage.show
    }
  }
}

object PdfCropperFx {

  var initialPdf: Option[String] = None

  def main(args: Array[String]) {
    if (args.length > 0)
      initialPdf = Some(args(0))
    Application.launch(classOf[PdfCropperFx], args: _*)
  }
}
