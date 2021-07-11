/*
 * Copyright 2021 Paolo Boni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray
package json
package derived

import spray.json.{JsString, JsValue, JsonFormat}

import spray.json.derived.MkJsonFormat

import scala.deriving._
import scala.compiletime._

object LazyMk {
  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def summonFormat[A](context: Context): JsonFormat[A] = summonFrom {
    case format: JsonFormat[A]     => format
    case mkFormat: MkJsonFormat[A] => mkFormat.value(context)
  }

  inline def summonAllFormats[A <: Tuple](context: Context): List[JsonFormat[_]] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonFormat[t](context) :: summonAllFormats[ts](context)

  inline def summonAllLabels[A <: Tuple]: List[String] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => label[t] :: summonAllLabels[ts]

  inline def readElems[T](
      p: Mirror.ProductOf[T]
  )(labels: List[String], formats: List[JsonFormat[_]])(json: JsValue): T = {
    val decodedElems = (labels zip formats).map { case (label, format) =>
      format.read(json.asJsObject.fields.getOrElse(label, JsNull))
    }
    p.fromProduct(Tuple.fromArray(decodedElems.toArray).asInstanceOf)
  }

  inline def writeElems[T](configuration: Configuration, formats: List[JsonFormat[_]])(obj: T): JsValue = {
    val pElem = obj.asInstanceOf[Product]
    (pElem.productElementNames.toList zip pElem.productIterator.toList zip formats)
      .map { case ((label, elem), format) =>
        val encoded = format.asInstanceOf[JsonFormat[Any]].write(elem)
        elem match {
          case None if !configuration.renderNullOptions =>
            JsObject.empty
          case e =>
            JsObject(label -> format.asInstanceOf[JsonFormat[Any]].write(e))
        }
      }
      .foldLeft(JsObject.empty) { case (obj, encoded) =>
        JsObject(obj.fields ++ encoded.fields)
      }
  }

  inline def writeCases[T](
      s: Mirror.SumOf[T]
  )(context: Context, labels: List[String], formats: List[JsonFormat[_]])(obj: T): JsValue = {
    val ord    = s.ordinal(obj)
    val format = formats(ord).asInstanceOf[JsonFormat[T]]
    format.write(obj) match {
      case res: JsString =>
        JsObject(context.discriminator.name -> res)
      case obj: JsObject =>
        JsObject(obj.fields + (context.discriminator.name -> JsString(labels(ord))))
      case _ => deserializationError(s"unexpected failure while encoding $obj")
    }
  }

  inline def readCases[T](context: Context, labels: List[String], formats: List[JsonFormat[_]])(json: JsValue): T =
    json match {
      case JsString(value) if labels.contains(value) =>
        val ord = labels.zipWithIndex.toMap.apply(value)
        formats(ord).asInstanceOf[JsonFormat[T]].read(json)
      case obj: JsObject =>
        obj.fields.get(context.discriminator.name) match {
          case Some(JsString(value)) if labels.contains(value) =>
            val ord = labels.zipWithIndex.toMap.apply(value)
            formats(ord).asInstanceOf[JsonFormat[T]].read(json)
          case Some(JsString(discriminatorValue)) =>
            deserializationError(
              s"""failed to decode ${context.typeName}: ${context.discriminator.name}="$discriminatorValue" is not defined"""
            )
          case _ =>
            deserializationError(
              s"""Failed to decode ${context.typeName}: discriminator "${context.discriminator.name}" not found"""
            )
        }
      case _ => deserializationError(s"unexpected failure while decoding $json")
    }

  inline given derived[T](using m: Mirror.Of[T], configuration: Configuration): LazyMk[T] = {
    LazyMk(MkJsonFormat(context => {
      lazy val formats = summonAllFormats[m.MirroredElemTypes](context)
      lazy val labels  = summonAllLabels[m.MirroredElemLabels]
      new JsonFormat[T] {
        override def read(json: JsValue): T = inline m match {
          case s: Mirror.SumOf[T]     => readCases[T](context, labels, formats)(json)
          case p: Mirror.ProductOf[T] => readElems(p)(labels, formats)(json)
        }
        override def write(obj: T): JsValue = inline m match {
          case s: Mirror.SumOf[T]     => writeCases(s)(context, labels, formats)(obj)
          case p: Mirror.ProductOf[T] => writeElems(configuration, formats)(obj)
        }
      }
    }))
  }
}

case class LazyMk[T](mkJsonFormat: MkJsonFormat[T])

// workaround to support recursive-types
// see https://github.com/lampepfl/dotty/issues/8183
given lazyDerived[T](using wrapper: => LazyMk[T]): MkJsonFormat[T] = new MkJsonFormat[T](context =>
  new JsonFormat[T] {
    override def read(json: JsValue): T = wrapper.mkJsonFormat.value(context).read(json)
    override def write(obj: T): JsValue = wrapper.mkJsonFormat.value(context).write(obj)
  }
)
