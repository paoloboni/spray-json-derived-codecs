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

  inline def readElems[NamesAndElems <: Tuple](context: Context)(json: JsValue): List[Any] =
    inline erasedValue[NamesAndElems] match {
      case _: (Tuple2[name, elem] *: elems1) =>
        lazy val elemFormat = summonFormat[elem](context)
        elemFormat.read(json.asJsObject.fields.getOrElse(label[name], JsNull)) :: readElems[elems1](context)(json)
      case _ => Nil
    }

  inline def writeElems[NamesAndElems <: Tuple](n: Int, context: Context, configuration: Configuration)(
      x: Any
  ): JsValue =
    inline erasedValue[NamesAndElems] match {
      case _: (Tuple2[name, elem] *: elems1) =>
        val e: elem         = x.asInstanceOf[Product].productElement(n).asInstanceOf[elem]
        lazy val elemFormat = summonFormat[elem](context)
        (e, writeElems[elems1](n + 1, context, configuration)(x)) match {
          case (None, JsObject(fields)) if !configuration.renderNullOptions =>
            JsObject(fields)
          case (_, JsObject(fields)) =>
            JsObject(fields + (label[name] -> elemFormat.write(e)))
          case (None, other) if !configuration.renderNullOptions =>
            JsObject.empty
          case (_, other) =>
            JsObject(label[name] -> elemFormat.write(e))
        }
      case _ =>
        JsNull
    }

  inline def derivedProduct[T](p: Mirror.ProductOf[T], configuration: Configuration): LazyMk[T] = LazyMk(
    new MkJsonFormat[T](context =>
      new JsonFormat[T] {
        override def write(t: T): JsValue = {
          inline p match {
            case m: Mirror.Singleton => JsString(constValue[m.MirroredLabel])
            case m: Mirror.Product =>
              writeElems[Tuple.Zip[m.MirroredElemLabels, m.MirroredElemTypes]](0, context, configuration)(t)
          }
        }

        override def read(json: JsValue): T = inline p match {
          case m: Mirror.Product =>
            val res = readElems[Tuple.Zip[m.MirroredElemLabels, m.MirroredElemTypes]](context)(json)
            p.fromProduct(Tuple.fromArray(res.toArray).asInstanceOf)
        }
      }
    )
  )

  inline def processWriteCases[NamesAndAlts <: Tuple](context: Context)(x: Any): JsValue =
    inline erasedValue[NamesAndAlts] match {
      case _: (Tuple2[name, alt] *: alts1) =>
        lazy val altFormat = summonFormat[`alt`](context)
        x match {
          case a: alt =>
            altFormat.write(a) match {
              case res: JsString =>
                JsObject(context.discriminator.name -> res)
              case obj: JsObject =>
                JsObject(obj.fields + (context.discriminator.name -> JsString(label[name])))
              case _ => deserializationError(s"unexpected failure while encoding $x")
            }
          case _ => processWriteCases[alts1](context)(x)
        }
      case _ => throw MatchError("failed to process case")
    }

  inline def summonFormat[A](context: Context): JsonFormat[A] = summonFrom {
    case format: JsonFormat[A]     => format
    case mkFormat: MkJsonFormat[A] => mkFormat.value(context)
  }

  inline def processReadCases[NamesAndAlts <: Tuple](context: Context)(json: JsValue): Any =
    inline erasedValue[NamesAndAlts] match {
      case _: (Tuple2[name, alt] *: alts1) =>
        lazy val altFormat = summonFormat[`alt`](context)
        json match {
          case JsString(value) if label[name] == value =>
            altFormat.read(json)
          case JsString(value) =>
            processReadCases[alts1](context)(json)
          case obj: JsObject =>
            obj.fields.get(context.discriminator.name) match {
              case Some(JsString(value)) if value == label[name] =>
                altFormat.read(json)
              case Some(_) => processReadCases[alts1](context)(json)
              case None =>
                deserializationError(
                  s"""Failed to decode ${context.typeName}: discriminator "${context.discriminator.name}" not found"""
                )
            }
          case _ => deserializationError(s"unexpected failure while decoding $json")
        }
      case _ =>
        val discriminatorValue = json.asJsObject.fields(context.discriminator.name).toString()
        deserializationError(
          s"failed to decode ${context.typeName}: ${context.discriminator.name}=$discriminatorValue is not defined"
        )
    }

  inline def deriveSum[T](s: Mirror.SumOf[T]): LazyMk[T] = LazyMk(
    new MkJsonFormat[T](context =>
      new JsonFormat[T] {
        override def read(json: JsValue): T =
          processReadCases[Tuple.Zip[s.MirroredElemLabels, s.MirroredElemTypes]](context)(json).asInstanceOf[T]

        override def write(obj: T): JsValue =
          processWriteCases[Tuple.Zip[s.MirroredElemLabels, s.MirroredElemTypes]](context)(obj)
      }
    )
  )

  inline given derived[T](using m: Mirror.Of[T], configuration: Configuration): LazyMk[T] = inline m match {
    case s: Mirror.SumOf[T]     => deriveSum(s)
    case p: Mirror.ProductOf[T] => derivedProduct(p, configuration)
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
