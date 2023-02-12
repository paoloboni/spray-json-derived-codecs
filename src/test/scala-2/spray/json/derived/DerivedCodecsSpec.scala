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

package spray.json.derived

import org.scalacheck.{Arbitrary, Gen, ScalacheckShapeless}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import spray.json._

import scala.reflect.ClassTag

class DerivedCodecsSpec
    extends AnyFeatureSpec
    with ScalaCheckDrivenPropertyChecks
    with ScalacheckShapeless
    with Matchers
    with TypeCheckedTripleEquals
    with CheckRoundTrip {

  implicit val arbitraryString: Arbitrary[String] = Arbitrary(Gen.alphaNumStr)

  Feature("encoding andThen decoding = identity") {

    Scenario("product type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      case class Cat(name: String, livesLeft: Int)
      forAll {
        cat: Cat =>
          checkRoundTrip[Cat](
            cat,
            s"""{"name": "${cat.name}", "livesLeft": ${cat.livesLeft}}"""
          )
      }
    }

    Scenario("tuple type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      type Cat = (String, Int)
      forAll {
        cat: Cat =>
          checkRoundTrip[Cat](
            cat,
            s"""["${cat._1}", ${cat._2}]"""
          )
      }
    }

    Scenario("sum types") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      forAll {
        animal: Cat =>
          checkRoundTrip[Animal](
            animal,
            s"""{"type": "Cat", "name": "${animal.name}", "livesLeft": ${animal.livesLeft}}"""
          )
      }: Unit
      forAll {
        animal: Dog =>
          checkRoundTrip[Animal](
            animal,
            s"""{"type": "Dog", "name": "${animal.name}", "bonesHidden": ${animal.bonesHidden}}"""
          )
      }: Unit
    }

    Scenario("sum types with discriminator") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      @Discriminator("animalType")
      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      forAll {
        animal: Cat =>
          checkRoundTrip[Animal](
            animal,
            s"""{"animalType": "Cat", "name": "${animal.name}", "livesLeft": ${animal.livesLeft}}"""
          )
      }: Unit
      forAll {
        animal: Dog =>
          checkRoundTrip[Animal](
            animal,
            s"""{"animalType": "Dog", "name": "${animal.name}", "bonesHidden": ${animal.bonesHidden}}"""
          )
      }: Unit
    }

    Scenario("sum types when discriminator indicates non-existing type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int) extends Animal

      forAll {
        `type`: String =>
          whenever(`type` != "Cat") {
            val ex = intercept[DeserializationException](s"""{"type": "${`type`}"}""".parseJson.convertTo[Animal])
            ex.getMessage should ===(
              s"""failed to decode ${implicitly[ClassTag[Animal]].toString()}: type="${`type`}" is not defined"""
            )
          }
      }: Unit
    }

    Scenario("sum types when discriminator not found") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int) extends Animal

      forAll {
        discriminator: String =>
          whenever(discriminator != "type") {
            val ex =
              intercept[DeserializationException](s"""{"$discriminator": "any"}""".parseJson.convertTo[Animal])
            ex.getMessage should ===(
              s"""Failed to decode ${implicitly[ClassTag[Animal]].toString()}: discriminator "type" not found"""
            )
          }
      }: Unit
    }

    Scenario("recursive types #1") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Tree
      case class Leaf(s: String)            extends Tree
      case class Node(lhs: Tree, rhs: Tree) extends Tree

      forAll {
        tree: Leaf =>
          checkRoundTrip[Tree](
            tree,
            s"""{"type": "Leaf", "s": "${tree.s}"}"""
          )
      }: Unit
      checkRoundTrip[Tree](
        Node(Node(Leaf("1"), Leaf("2")), Leaf("3")),
        s"""{
           |  "lhs": {
           |    "lhs": {
           |      "s": "1",
           |      "type": "Leaf"
           |    },
           |    "rhs": {
           |      "s": "2",
           |      "type": "Leaf"
           |    },
           |    "type": "Node"
           |  },
           |  "rhs": {
           |    "s": "3",
           |    "type": "Leaf"
           |  },
           |  "type": "Node"
           |}
           |""".stripMargin
      )
    }

    Scenario("recursive types #2") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait AList[+T]
      case object ANil                             extends AList[Nothing]
      case class ACons[T](head: T, tail: AList[T]) extends AList[T]

      checkRoundTrip[AList[Int]](
        ACons(1, ACons(2, ANil)),
        """{"head":1,"tail":{"head":2,"tail":{"type":"ANil"},"type":"ACons"},"type":"ACons"}"""
      )
    }

    Scenario("polymorphic types") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      case class Quux[A](value: A)
      forAll { quux: Quux[String] =>
        checkRoundTrip[Quux[String]](
          quux,
          s"""{"value": "${quux.value}"}"""
        )
      }: Unit
      forAll { quux: Quux[Int] =>
        checkRoundTrip[Quux[Int]](
          quux,
          s"""{"value": ${quux.value}}"""
        )
      }: Unit
    }

    Scenario("option values default rendering") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      case class Dog(toy: Option[String])

      checkRoundTrip[Dog](Dog(Some("ball")), """{"toy": "ball"}"""): Unit
      Dog(toy = None).toJson.compactPrint should ===("""{}"""): Unit

      checkRoundTrip[Dog](Dog(None), """{}"""): Unit
    }

    Scenario("option values render nulls") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      new derived.WithConfiguration {
        implicit val configuration: Configuration = Configuration(renderNullOptions = true)
        case class Dog(toy: Option[String])
        Dog(toy = None).toJson.compactPrint should ===("""{"toy":null}"""): Unit
      }
    }

    Scenario("semi-auto derivation") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.semiauto._

      case class Cat(name: String, livesLeft: Int)

      implicit val format: JsonFormat[Cat] = deriveFormat[Cat]

      checkRoundTrip(Cat("Oliver", 7), """{"livesLeft":7,"name":"Oliver"}""")
    }

    Scenario("compiler error on derivation failure") {
      import spray.json.derived.semiauto._

      case class Cat(name: String, livesLeft: Int)

      shapeless.test.illTyped(
        "implicit val format: JsonFormat[Cat] = deriveFormat[Cat]",
        "Cannot derive instance JsonFormat\\[Cat\\]"
      )
    }
  }
}
