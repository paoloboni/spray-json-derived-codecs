/*
 * Copyright 2020 Paolo Boni
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

import shapeless._
import shapeless.labelled.{FieldType, field}

class MkJsonFormat[T](val value: Discriminator => JsonFormat[T]) extends AnyVal

object MkJsonFormat {
  def apply[T](implicit format: MkJsonFormat[T]): MkJsonFormat[T] = format

  implicit def cnilInstance: MkJsonFormat[CNil] =
    new MkJsonFormat[CNil](_ =>
      new JsonFormat[CNil] {
        override def write(obj: CNil): JsValue = obj.impossible
        override def read(json: JsValue): CNil = throw new Exception("impossible")
      }
    )

  implicit def coproductInstance[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hInstance: Lazy[MkJsonFormat[H]],
      tInstance: MkJsonFormat[T]
  ): MkJsonFormat[FieldType[K, H] :+: T] =
    new MkJsonFormat[FieldType[K, H] :+: T](discriminator =>
      new JsonFormat[FieldType[K, H] :+: T] {
        override def write(obj: FieldType[K, H] :+: T): JsValue = obj match {
          case Inl(head) =>
            JsObject(
              hInstance.value.value(discriminator).write(head).asJsObject.fields +
                (discriminator.name -> JsString(witness.value.name))
            )
          case Inr(tail) => tInstance.value(discriminator).write(tail)
        }
        override def read(json: JsValue): FieldType[K, H] :+: T =
          json.asJsObject.fields(discriminator.name) match {
            case JsString(value) if value == witness.value.name =>
              Inl(field[K](hInstance.value.value(discriminator).read(json)))
            case _ => Inr(tInstance.value(discriminator).read(json))
          }
      }
    )

  implicit def hNilInstance: MkJsonFormat[HNil] =
    new MkJsonFormat[HNil](_ =>
      new JsonFormat[HNil] {
        override def read(json: JsValue): HNil = HNil
        override def write(obj: HNil): JsValue = JsObject.empty
      }
    )

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[JsonFormat[H]],
      tEncoder: MkJsonFormat[T]
  ): MkJsonFormat[FieldType[K, H] :: T] =
    new MkJsonFormat[FieldType[K, H] :: T](discriminator =>
      new JsonFormat[FieldType[K, H] :: T] {
        override def read(json: JsValue): FieldType[K, H] :: T =
          field[K](hEncoder.value.read(json.asJsObject.fields(witness.value.name))) :: tEncoder
            .value(discriminator)
            .read(json)
        override def write(obj: FieldType[K, H] :: T): JsValue = obj match {
          case h :: t if h.isInstanceOf[None.type] => tEncoder.value(discriminator).write(t)
          case h :: t =>
            tEncoder.value(discriminator).write(t) match {
              case JsObject(fields) => JsObject(fields + (witness.value.name -> hEncoder.value.write(h)))
              case _                => throw new Exception("impossible")
            }
        }
      }
    )

  implicit def genericEncoder[T, Repr](implicit
      gen: LabelledGeneric.Aux[T, Repr],
      rEncoder: Lazy[MkJsonFormat[Repr]]
  ): MkJsonFormat[T] = {
    new MkJsonFormat[T](discriminator =>
      new JsonFormat[T] {
        override def write(obj: T): JsValue = rEncoder.value.value(discriminator).write(gen.to(obj))

        override def read(json: JsValue): T = gen.from(rEncoder.value.value(discriminator).read(json))
      }
    )
  }
}

case class Discriminator(name: String) extends scala.annotation.StaticAnnotation

object Discriminator {
  val default: Discriminator = Discriminator("type")
}
