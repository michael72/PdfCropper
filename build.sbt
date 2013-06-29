name := "PdfCropper"

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
	"org.jaylib.scala.config" %% "configbase" % "1.0.0",
	"org.jaylib.scala.config" %% "configmacros" % "1.0.0" % "compile",
	"com.itextpdf" % "itextpdf" % "5.4.2",
	"org.jfxtras" % "jfxtras-labs" % "2.2-r5"
	)
	
// Add JavaFX Runtime as an unmanaged dependency, hoping to find it in the JRE's library folder.
unmanagedJars in Compile ++= Seq(new File(System.getProperty("java.home")) / "lib" / "jfxrt.jar")
