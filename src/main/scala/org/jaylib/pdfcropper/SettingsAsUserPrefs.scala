package org.jaylib.pdfcropper
import java.util.prefs.Preferences.userNodeForPackage

trait SettingsAsUserPrefs {
  private val prefs = userNodeForPackage(getClass)
  import UseAsPreferencesImplicits._
  import java.util.prefs.Preferences
  abstract class Prefs[T] extends UserPrefs[Preferences,T]
  private val createPrefs = UserPrefs(prefs)

  /**
   * the number of doubled pixel-size lines that are together at the
   * end of the current and at the start of the next split
   */
  protected val pagesBuffer = createPrefs("pagesBuffer", 5)

  protected val callExec = createPrefs("callExec", true)

  protected val leaveCover = createPrefs("leaveCover", true)

  protected val rotateSplitPages = createPrefs("rotateSplitPages", 270)

  protected val twoPages = createPrefs("twoPages", false);
  
  protected val activeEditor = createPrefs("activeEditor", 1); // 1 = left, 2 = right, 3 = both
  
  protected val sameHeight = createPrefs("sameHeight", true);

  
  /** the number of pages considered when doing an auto crop */
  protected val autoPagesNumber = createPrefs("autoPagesNumber", 5)

    /**
   * Gets the saved initial directory as String or an empty String if nothing was saved.
   */
  protected val initDir = createPrefs("initDir", "")
  
}