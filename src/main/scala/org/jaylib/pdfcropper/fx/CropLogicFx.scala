package org.jaylib.pdfcropper.fx
import java.awt.image.BufferedImage
import java.io.File
import org.jaylib.pdfcropper.CropLogic
import org.jaylib.pdfcropper.UserPrefs
import org.jaylib.pdfcropper.Utils.function2EventHandler
import javafx.beans.property.{ Property, SimpleBooleanProperty, SimpleIntegerProperty }
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.collections.FXCollections
import javafx.embed.swing.SwingFXUtils
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.geometry.Pos.CENTER
import javafx.scene.Scene
import javafx.scene.control.{ Button, CheckBox, ChoiceBox, Control, Label }
import javafx.scene.layout.{ GridPane, VBox }
import javafx.scene.text.Text
import javafx.stage.{ FileChooser, Modality, Stage }
import jfxtras.labs.scene.control.ListSpinner
import javafx.scene.control.TextField
import com.sun.javafx.scene.control.skin.TextInputControlSkin
import com.sun.javafx.scene.control.behavior.TextInputControlBehavior
import javafx.scene.control.Skin
import org.jaylib.pdfcropper.UserPrefsImplicits._

class CropLogicFx(
    protected val stage: Stage,
    protected val fc: FileChooser,
    val imvBox: ImageContainer) extends CropLogic {

  imvBox.init(_ => doublePages)
  protected val choiceTwo: ChoiceBox[String] = createChoiceBox(
    Array("1 Page View", "2 Pages - Keys Left", "2 Pages - Keys Right", "2 Pages - Keys Both"),
    Array(0, 1, 2, 3), getViewChoice, { value => updateViewChoice(value) })

  private def updateStage {
    stage.setWidth(imvBox.getPreferredWidth.max(480))
    stage.setHeight(imvBox.getImageHeight + 120)
  }
  override protected def onUpdateImage(image: BufferedImage, index: Int) {
    imvBox.imvs(index).setImage(SwingFXUtils.toFXImage(image, null))
    updateStage
  }

  override protected def onClearImage(index: Int) {
    imvBox.imvs(index).setImage(SwingFXUtils.toFXImage(
      new BufferedImage(imvBox.imvs(index).getImage.getWidth.toInt, imvBox.imvs(index).getImage.getHeight.toInt, BufferedImage.TYPE_INT_ARGB), null))
    updateStage
  }

  override def file_=(newFile: File) {
    try {
      super.file = newFile
      stage.setTitle("Pdf Cropper - " + newFile.getName);
    }
    catch {
      case t: Throwable =>
        t.printStackTrace
        new Stage {
          initModality(Modality.APPLICATION_MODAL)
          setScene(new Scene(
            new VBox {
              getChildren.add(new Text("Unable to load " + file.getAbsolutePath
                + "\nMaybe the file is protected."))
              getChildren.add(createButton(close, "Try another file"))
              setAlignment(CENTER)
              setPadding(new Insets(5))
            }))
        }.showAndWait
        super.file = fc.showOpenDialog(stage)
    }
  }

  def createButton(action: => Any, txt: String = "") = new Button(txt) {
    setOnAction((_: ActionEvent) => action)
  }
  def createSpinner(getter: => Int, setter: Int => Unit, min: Int, max: Int) = {
    val spinner = new ListSpinner[Integer](min, max)
    spinner.valueProperty.bindBidirectional(new SimpleIntegerProperty {
      override def get = getter
      override def set(value: Int) = setter
    }.asInstanceOf[Property[java.lang.Integer]])
    spinner
  }

  def createCheckBox(getter: => Boolean, setter: Boolean => Unit, text: String = "") = {
    val check = new CheckBox(text)
    check.selectedProperty.bindBidirectional(new SimpleBooleanProperty {
      override def get = getter
      override def set(value: Boolean) = setter(value)
    })
    check
  }

  def createChoiceBox(entries: Array[String], values: Array[Int], getter: => Int, setter: Int => Unit) = {
    import scala.collection.JavaConversions._
    val choice = new ChoiceBox(FXCollections.observableList(entries.toList))
    choice.getSelectionModel.selectedIndexProperty.addListener(new ChangeListener[Number] {
      override def changed(ov: ObservableValue[_ <: Number], oldVal: Number, newVal: Number) {
        setter(values(newVal.asInstanceOf[Int]))
      }
    })
    choice.getSelectionModel().select(values.indexOf(getter))
    choice
  }

  override def configOptions {
    new Stage {
      initModality(Modality.WINDOW_MODAL)
      setScene(new Scene(
        new VBox {
          setPadding(new Insets(10, 10, 10, 10))
          setAlignment(CENTER)
          getChildren.addAll(new GridPane {
            setAlignment(CENTER)
            setHgap(10);
            setVgap(10);
            setPadding(new Insets(0, 10, 0, 10));
            private var row = 0
            def addItem(name: String, item: Control) {
              add(new Label(name), 0, row)
              add(item, 1, row)
              row += 1
            }
            addItem("Leave Coverpage uncropped", createCheckBox(leaveCover, leaveCover))
            addItem("Open PDF reader after export", createCheckBox(callExec.value, callExec.value = _))
            addItem("Rotate split pages by", createChoiceBox(
              Array("Do not rotate", "90deg (right)", "270deg (left)"), Array(0, 90, 270), rotateSplitPages.value, rotateSplitPages.value = _))

            addItem("Buffer between split pages", createSpinner(pagesBuffer, pagesBuffer, 1, 100))
            addItem("Number of Pages in AutoCrop", createSpinner(autoPagesNumber, autoPagesNumber, 2, 100))

            val myTextField = new TextField()

            def parseDouble(s: String) = try {
              Some(s.toDouble)
            }
            catch {
              case _ => None
            }

          }, createButton(close, "OK"))
        }))

      def update {
        onUpdateDoublePages
      }

      override def close {
        update
        super.close
      }
      override def hide {
        update
        super.hide
      }

    }.showAndWait
  }

  def updateViewChoice(index: Int) {
    if (index != getViewChoice) {
      val old = doublePages
      if (index == 0)
        doublePages = false
      else {
        doublePages = true
        activeEditor.value = index
      }
      if (old != doublePages)
        onUpdateDoublePages
    }
    imvBox.updateBorderStyle(twoPages, activeEditor)
  }

  def swapActiveEdit {
    choiceTwo.getSelectionModel().select((getViewChoice + 1) & 3)
  }
  def getViewChoice(): Int = {
    if (!doublePages) 0
    else activeEditor
  }
  def onUpdateDoublePages = {
    choiceTwo.getSelectionModel().select(getViewChoice())
    updateImages()
  }
  lazy val buttons = Array[Control](
    createButton(exec(exportFile(1)), "Export"),
    createButton(exec(exportFile(2)), "Export 2"),
    createButton(reload, "Reset"),
    createButton(file = fc.showOpenDialog(stage), "Load"),
    choiceTwo)
}