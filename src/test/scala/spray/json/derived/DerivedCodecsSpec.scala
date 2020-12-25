/*
 * Copyright 2020 io.github.paoloboni
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

    scenario("tuple type") {
      type Cat = (String, Int)
      forAll { cat: Cat =>
        checkRoundtrip[Cat](
          cat,
          s"""["${cat._1}", ${cat._2}]"""
        )
      }
    }

    scenario("sum types") {
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

    scenario("sum types with discriminator") {
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

    scenario("recursive types") {
      sealed trait Tree
      case class Leaf(s: String)            extends Tree
      case class Node(lhs: Tree, rhs: Tree) extends Tree

      pending
//      forAll { tree: Leaf =>
//        checkRoundtrip[Tree](
//          tree,
//          s"""{"type": "Leaf", "s": "${tree.s}"}"""
//        )
//      }
    }

    scenario("polylmorphic types") {
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