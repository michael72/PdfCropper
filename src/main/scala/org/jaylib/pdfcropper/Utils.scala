package org.jaylib.pdfcropper

import javafx.event.EventHandler
import javafx.event.ActionEvent

object Utils {
  import scala.language.implicitConversions
  import scala.language.reflectiveCalls

  implicit def function2ActionHandler(f: => Any) = new EventHandler[ActionEvent] {
    def handle(event: ActionEvent) { f }
  }

  implicit def function2EventHandler[T <: javafx.event.Event](f: T => Any) =
    new EventHandler[T] {
      def handle(event: T) { f(event) }
    }

  def using[T <: { def close() }, R](resource: T)(workOn: T => R) {
    try {
      workOn(resource)
    }
    finally {
      if (resource != null) resource.close
    }
  }
  def openUsing[T <: { def open(); def close() }, R](resource: T)(workOn: T => R) {
    try {
      resource.open
      workOn(resource)
    }
    finally {
      if (resource != null) resource.close
    }
  }
  def usingTempFile[T](file: java.io.File)(workOn: java.io.File => T) = {
    try {
      workOn(file)
    }
    finally {
      if (workOn != null)
        file.delete
    }
  }
}