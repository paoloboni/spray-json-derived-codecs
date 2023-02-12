/*
 * Copyright 2023 Paolo Boni
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

import scala.annotation.{implicitNotFound, nowarn}

@implicitNotFound("Cannot derive instance JsonFormat[${T}]")
class MkJsonFormat[T](val value: Context => JsonFormat[T]) extends AnyVal

trait LowPriority {
  implicit def hlistEncoder0[K <: Symbol, H, T <: HList, R](implicit
      witness: Witness.Aux[K],
      gen: LabelledGeneric.Aux[H, R],
      hEncoder: Lazy[MkJsonFormat[R]],
      tEncoder: MkJsonFormat[T],
      configuration: Configuration
  ): MkJsonFormat[FieldType[K, H] :: T] =
    new MkJsonFormat[FieldType[K, H] :: T](context =>
      new JsonFormat[FieldType[K, H] :: T] {
        override def read(json: JsValue): FieldType[K, H] :: T =
          field[K](
            gen.from(hEncoder.value.value(context).read(json.asJsObject.fields(witness.value.name)))
          ) :: tEncoder
            .value(context)
            .read(json)
        override def write(obj: FieldType[K, H] :: T): JsValue = obj match {
          case h :: t if h.isInstanceOf[None.type] && !configuration.renderNullOptions =>
            tEncoder.value(context).write(t)
          case h :: t =>
            tEncoder.value(context).write(t) match {
              case JsObject(fields) =>
                JsObject(fields + (witness.value.name -> hEncoder.value.value(context).write(gen.to(h))))
              case _ => throw new Exception("impossible")
            }
        }
      }
    )
}

object MkJsonFormat extends LowPriority {
  def apply[T](implicit format: MkJsonFormat[T]): MkJsonFormat[T] = format

  implicit def cnilInstance: MkJsonFormat[CNil] =
    new MkJsonFormat[CNil](context =>
      new JsonFormat[CNil] {
        override def write(obj: CNil): JsValue = obj.impossible
        override def read(json: JsValue): CNil = {
          val discriminatorValue = json.asJsObject.fields(context.discriminator.name).toString()
          throw DeserializationException(
            s"failed to decode ${context.typeName}: ${context.discriminator.name}=$discriminatorValue is not defined"
          )
        }
      }
    )

  implicit def coproductInstance[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hInstance: Lazy[MkJsonFormat[H]],
      tInstance: MkJsonFormat[T]
  ): MkJsonFormat[FieldType[K, H] :+: T] =
    new MkJsonFormat[FieldType[K, H] :+: T](context =>
      new JsonFormat[FieldType[K, H] :+: T] {
        override def write(obj: FieldType[K, H] :+: T): JsValue = obj match {
          case Inl(head) =>
            JsObject(
              hInstance.value.value(context).write(head).asJsObject.fields +
                (context.discriminator.name -> JsString(witness.value.name))
            )
          case Inr(tail) => tInstance.value(context).write(tail)
        }
        override def read(json: JsValue): FieldType[K, H] :+: T =
          json.asJsObject.fields.get(context.discriminator.name) match {
            case Some(JsString(value)) if value == witness.value.name =>
              Inl(field[K](hInstance.value.value(context).read(json)))
            case Some(_) => Inr(tInstance.value(context).read(json))
            case None =>
              throw DeserializationException(
                s"""Failed to decode ${context.typeName}: discriminator "${context.discriminator.name}" not found"""
              )
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

  implicit def hlistEncoder1[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[JsonFormat[H]],
      tEncoder: MkJsonFormat[T],
      configuration: Configuration
  ): MkJsonFormat[FieldType[K, H] :: T] =
    new MkJsonFormat[FieldType[K, H] :: T](context =>
      new JsonFormat[FieldType[K, H] :: T] {
        override def read(json: JsValue): FieldType[K, H] :: T =
          field[K](hEncoder.value.read(json.asJsObject.fields.getOrElse(witness.value.name, JsNull))) :: tEncoder
            .value(context)
            .read(json)
        override def write(obj: FieldType[K, H] :: T): JsValue = obj match {
          case h :: t if h.isInstanceOf[None.type] && !configuration.renderNullOptions =>
            tEncoder.value(context).write(t)
          case h :: t =>
            tEncoder.value(context).write(t) match {
              case JsObject(fields) => JsObject(fields + (witness.value.name -> hEncoder.value.write(h)))
              case _                => throw new Exception("impossible")
            }
        }
      }
    )

  implicit def genericEncoder[T, Repr](implicit
      gen: LabelledGeneric.Aux[T, Repr],
      rEncoder: Lazy[MkJsonFormat[Repr]],
      @nowarn configuration: Configuration
  ): MkJsonFormat[T] = {
    new MkJsonFormat[T](context =>
      new JsonFormat[T] {
        override def write(obj: T): JsValue = rEncoder.value.value(context).write(gen.to(obj))

        override def read(json: JsValue): T = gen.from(rEncoder.value.value(context).read(json))
      }
    )
  }
}
