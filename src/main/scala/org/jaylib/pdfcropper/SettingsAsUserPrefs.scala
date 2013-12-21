package org.jaylib.pdfcropper
import org.jaylib.scala.config.macros.ConfigMacros
import org.jaylib.scala.config.preferences.PreferencesConfig

trait SettingsAsUserPrefs {
  /**
   * the number of doubled pixel-size lines that are together at the
   * end of the current and at the start of the next split
   */
  var pagesBuffer : Int

  var callExec : Boolean

  var leaveCover : Boolean

  var rotateSplitPages : Int

  var twoPages : Boolean
  
  var activeEditor : Int // 1 = left, 2 = right, 3 = both
  
  var sameHeight : Boolean

  /** the number of pages considered when doing an auto crop */
  var autoPagesNumber : Int

  /**
   * Gets the saved initial directory as String or an empty String if nothing was saved.
   */
  var initDir: String
  
  /**
   * Location of the ghostscript installation or just the name of ghostscript executable - if the executable is on the system path.
   */
  var ghostscript : String
}

class PdfCropperSettings

object SettingsAsUserPrefs {
  val defaults = Map(
      "pagesBuffer" -> "5", 
      "callExec" -> "true", 
      "leaveCover" -> "true",
      "rotateSplitPages" -> "270", 
      "twoPages" -> "false",  
      "activeEditor" -> "1", 
      "sameHeight" -> "true", 
      "autoPagesNumber" -> "5",
      "initDir" -> "\"\"",
      "ghostscript" -> "gswin32c.exe")
  def apply() = {
	  val cfg = new PreferencesConfig(classOf[PdfCropperSettings], defaults)
	  ConfigMacros.wrap(classOf[SettingsAsUserPrefs], cfg.getProperty, cfg.setProperty)
  }
}