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

package spray.json.derived

import org.scalacheck.{Arbitrary, Gen, ScalacheckShapeless}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import shapeless.LabelledGeneric
import spray.json._

class DerivedCodecsSpec
    extends AnyFeatureSpec
    with ScalaCheckDrivenPropertyChecks
    with ScalacheckShapeless
    with Matchers
    with TypeCheckedTripleEquals
    with Instances
    with DefaultJsonProtocol {

  implicit val arbitraryString: Arbitrary[String] = Arbitrary(Gen.alphaNumStr)

  Feature("encoding andThen decoding = identity") {

    Scenario("product type") {
      case class Cat(name: String, livesLeft: Int)
      forAll { cat: Cat =>
        checkRoundtrip[Cat](
          cat,
          s"""{"name": "${cat.name}", "livesLeft": ${cat.livesLeft}}"""
        )
      }
    }

    Scenario("tuple type") {
      type Cat = (String, Int)
      forAll { cat: Cat =>
        checkRoundtrip[Cat](
          cat,
          s"""["${cat._1}", ${cat._2}]"""
        )
      }
    }

    Scenario("sum types") {
      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      forAll { animal: Cat =>
        checkRoundtrip[Animal](
          animal,
          s"""{"type": "Cat", "name": "${animal.name}", "livesLeft": ${animal.livesLeft}}"""
        )
      }
      forAll { animal: Dog =>
        checkRoundtrip[Animal](
          animal,
          s"""{"type": "Dog", "name": "${animal.name}", "bonesHidden": ${animal.bonesHidden}}"""
        )
      }
    }

    Scenario("sum types with discriminator") {
      @Discriminator("animalType")
      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      forAll { animal: Cat =>
        checkRoundtrip[Animal](
          animal,
          s"""{"animalType": "Cat", "name": "${animal.name}", "livesLeft": ${animal.livesLeft}}"""
        )
      }
      forAll { animal: Dog =>
        checkRoundtrip[Animal](
          animal,
          s"""{"animalType": "Dog", "name": "${animal.name}", "bonesHidden": ${animal.bonesHidden}}"""
        )
      }
    }

    Scenario("recursive types #1") {
      sealed trait Tree
      case class Leaf(s: String)            extends Tree
      case class Node(lhs: Tree, rhs: Tree) extends Tree

      forAll { tree: Leaf =>
        checkRoundtrip[Tree](
          tree,
          s"""{"type": "Leaf", "s": "${tree.s}"}"""
        )
      }
      checkRoundtrip[Tree](
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
      sealed trait AList[+T]
      case object ANil                             extends AList[Nothing]
      case class ACons[T](head: T, tail: AList[T]) extends AList[T]

      checkRoundtrip[AList[Int]](
        ACons(1, ACons(2, ANil)),
        """{"head":1,"tail":{"head":2,"tail":{"type":"ANil"},"type":"ACons"},"type":"ACons"}"""
      )
    }

    Scenario("polymorphic types") {
      case class Quux[A](value: A)
      forAll { quux: Quux[String] =>
        checkRoundtrip[Quux[String]](
          quux,
          s"""{"value": "${quux.value}"}"""
        )
      }
      forAll { quux: Quux[Int] =>
        checkRoundtrip[Quux[Int]](
          quux,
          s"""{"value": ${quux.value}}"""
        )
      }
    }
  }

  def checkRoundtrip[A: JsonFormat](a: A, expectedJson: String): Assertion = {
    val parsed: JsValue = expectedJson.parseJson
    a.toJson should ===(parsed)
    parsed.convertTo[A] should ===(a)
  }
}
