package org.jaylib.pdfcropper

	trait PrefsStorage {
	  def get(key: String, default: String): String
	  def put(key: String, newValue: String)
	}
	
	abstract class UserPrefs[P <% PrefsStorage, T] {
	  protected val prefs: P
	  protected val key: String
	  protected val default: T
	  private[this] var bufferedValue: T = fromString(prefs.get(key, stringFrom(default)))
	  // conversion from and to String
	  def fromString(str: String): T
	  def stringFrom(newValue: T): String = newValue.toString
	  // setter and getter
	  final def value_=(newValue: T) = { prefs.put(key, stringFrom(newValue)); bufferedValue = newValue }
	  final def value: T = bufferedValue
	  
	  final def getter() = value
	  final def setter = {value_=_}
	}
	
	abstract class DefaultUserPrefs[P <% PrefsStorage, T](protected val prefs: P, protected val key: String, protected val default: T) extends UserPrefs[P, T]
	
	class FunUserPrefs[P <% PrefsStorage, T](protected val prefs: P, protected val key: String, protected val default: T,
	                                         convertFromString: String => T) extends UserPrefs[P, T] {
	  def fromString(str: String) = convertFromString(str)
	}
	
	class IntUserPrefs[P <% PrefsStorage](protected val prefs: P, protected val key: String, protected val default: Int) extends UserPrefs[P, Int] {
	  def fromString(str: String) = str.toInt
	}
	class BooleanUserPrefs[P <% PrefsStorage](protected val prefs: P, protected val key: String, protected val default: Boolean) extends UserPrefs[P, Boolean] {
	  def fromString(str: String) = str.toBoolean
	}
	class StringUserPrefs[P <% PrefsStorage](protected val prefs: P, protected val key: String, protected val default: String) extends UserPrefs[P, String] {
	  def fromString(str: String) = str
	}
	class DoubleUserPrefs[P <% PrefsStorage](protected val prefs: P, protected val key: String, protected val default: Double) extends UserPrefs[P, Double] {
	  def fromString(str: String) = str.toDouble
	}
	
	// Helper object to create preferences
	object UserPrefs {
	  class UserPrefsCreate[P <% PrefsStorage](val prefs: P) {
	    def apply(key: String, default: Int) = new IntUserPrefs[P](prefs, key, default)
	    def apply(key: String, default: Boolean) = new BooleanUserPrefs[P](prefs, key, default)
	    def apply(key: String, default: String) = new StringUserPrefs[P](prefs, key, default)
	    def apply(key: String, default: Double) = new DoubleUserPrefs[P](prefs, key, default)
	    def apply[T](key: String, default: T, fun: String => T) = new FunUserPrefs[P, T](prefs, key, default, fun)
	  }
	  def apply(prefs: PrefsStorage) = new UserPrefsCreate(prefs)
	}
	
	object UseAsPreferencesImplicits {
	  import java.util.prefs.Preferences
	  import scala.language.implicitConversions
	  implicit def preferences2PrefsStorage(prefs: Preferences) = new PrefsStorage {
	    def get(key: String, default: String) = prefs.get(key, default)
	    def put(key: String, newValue: String) { prefs.put(key, newValue) }
	  }
	}
	
	object UserPrefsImplicits {
	  import scala.language.implicitConversions
	  implicit def UserPrefsToType[P <% PrefsStorage, T](up: UserPrefs[P, T]) = up.value
	  implicit def userPrefs2Getter[T](userPrefs: UserPrefs[PrefsStorage, T]) = userPrefs.getter
	  implicit def userPrefs2Setter[T](userPrefs: UserPrefs[PrefsStorage, T]) = userPrefs.setter
	}
	
	object TestUserPrefs extends App {
	  class Sample {
	    import UseAsPreferencesImplicits._
	    import java.util.prefs.Preferences.userNodeForPackage
	    // sample usage of UserPrefsCreate
	    protected val createPrefs = UserPrefs(userNodeForPackage(Sample.this.getClass))
	    // numTuple and numberOfCalls are saved to the preferences
	    val numTuple = createPrefs("numTuple", (1, 2), str => {
	      val idx = str.indexOf(',')
	      str.substring(1, idx).toInt -> str.substring(idx + 1, str.length - 1).toInt
	    })
	    val intArray = new DefaultUserPrefs(createPrefs.prefs, "intArray", Array(8, 9)) {
	      override def stringFrom(newValue: Array[Int]) = newValue.mkString(",")
	      def fromString(str: String) = str.split(',').map(_.toInt)
	    }
	    val numberOfCalls = createPrefs("numberOfCalls", 0)
	  }
	
	  val sample = new Sample
	  println(sample.numTuple.value) // (1,2) when executed the first time, (5,6) afterwards
	  sample.numTuple.value = (3, 4)
	  println(sample.numTuple.value) // (3,4)
	  sample.numTuple.value = (5, 6)
	  println(sample.numTuple.value) // (5,6)
	
	  println(sample.intArray.value.mkString(",")) //d8,9 first, then 1,2,3
	  sample.intArray.value = Array(1, 2, 3)
	
	  sample.numberOfCalls.value += 1 // incremented for each execution of TestUserPrefs
	  println("number of calls: " + sample.numberOfCalls.value)
	}
