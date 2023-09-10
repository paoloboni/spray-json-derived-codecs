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

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import spray.json._

import scala.reflect.ClassTag

class DerivedCodecsSpec
    extends AnyFeatureSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with TypeCheckedTripleEquals
    with CheckRoundTrip {

  Feature("encoding andThen decoding = identity") {

    Scenario("product type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      case class Cat(name: String, livesLeft: Int)

      val cat: Cat = Cat("Oliver", 7)
      checkRoundTrip[Cat](
        cat,
        s"""{"name": "${cat.name}", "livesLeft": ${cat.livesLeft}}"""
      )
    }

    Scenario("tuple type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      type Cat = (String, Int)
      val cat: Cat = ("Oliver", 7)
      checkRoundTrip[Cat](
        cat,
        s"""["${cat._1}", ${cat._2}]"""
      )
    }

    Scenario("sum types") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      val cat: Cat = Cat("Oliver", 7)
      checkRoundTrip[Animal](
        cat,
        s"""{"type": "Cat", "name": "${cat.name}", "livesLeft": ${cat.livesLeft}}"""
      )
      val dog: Dog = Dog("Albert", 3)
      checkRoundTrip[Animal](
        dog,
        s"""{"type": "Dog", "name": "${dog.name}", "bonesHidden": ${dog.bonesHidden}}"""
      )
    }

    Scenario("sum types with discriminator") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      @Discriminator("animalType")
      sealed trait Animal
      case class Cat(name: String, livesLeft: Int)   extends Animal
      case class Dog(name: String, bonesHidden: Int) extends Animal

      val cat: Cat = Cat("Oliver", 7)
      checkRoundTrip[Animal](
        cat,
        s"""{"animalType": "Cat", "name": "${cat.name}", "livesLeft": ${cat.livesLeft}}"""
      )
      val dog: Dog = Dog("Albert", 3)
      checkRoundTrip[Animal](
        dog,
        s"""{"animalType": "Dog", "name": "${dog.name}", "bonesHidden": ${dog.bonesHidden}}"""
      )
    }

    Scenario("sum types when discriminator indicates non-existing type") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int) extends Animal

      val ex = intercept[DeserializationException](s"""{"type": "not-cat"}""".parseJson.convertTo[Animal])
      ex.getMessage should ===(
        s"""failed to decode ${implicitly[ClassTag[Animal]].toString()}: type="not-cat" is not defined"""
      )
    }

    Scenario("sum types when discriminator not found") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Animal
      case class Cat(name: String, livesLeft: Int) extends Animal

      val ex =
        intercept[DeserializationException](s"""{"not-type": "any"}""".parseJson.convertTo[Animal])
      ex.getMessage should ===(
        s"""Failed to decode ${implicitly[ClassTag[Animal]].toString()}: discriminator "type" not found"""
      )
    }

    Scenario("recursive types #1") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      sealed trait Tree
      case class Leaf(s: String)            extends Tree
      case class Node(lhs: Tree, rhs: Tree) extends Tree

      val tree: Leaf = Leaf("leaf")
      checkRoundTrip[Tree](
        tree,
        s"""{"type": "Leaf", "s": "${tree.s}"}"""
      )
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

      import AList._
      checkRoundTrip[AList[Int]](
        ACons(1, ACons(2, ANil)),
        """{"head":1,"tail":{"head":2,"tail":{"type":"ANil"},"type":"ACons"},"type":"ACons"}"""
      )
    }

    Scenario("polymorphic types") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      val stringQuux = Quux("quux")
      checkRoundTrip[Quux[String]](
        stringQuux,
        s"""{"value": "${stringQuux.value}"}"""
      )
      val intQuux = Quux(1)
      checkRoundTrip[Quux[Int]](
        intQuux,
        s"""{"value": ${intQuux.value}}"""
      )
    }

    Scenario("option values default rendering") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      case class Dog(toy: Option[String])

      checkRoundTrip[Dog](Dog(Some("ball")), """{"toy": "ball"}""")
      Dog(toy = None).toJson.compactPrint should ===("""{}""")

      checkRoundTrip[Dog](Dog(None), """{}""")
    }

    Scenario("option values render nulls") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.auto._

      new derived.WithConfiguration {
        implicit val configuration: Configuration = Configuration(renderNullOptions = true)
        case class Dog(toy: Option[String])
        Dog(toy = None).toJson.compactPrint should ===("""{"toy":null}""")
      }
    }

    Scenario("semi-auto derivation") {
      import spray.json.DefaultJsonProtocol._
      import spray.json.derived.semiauto._

      case class Cat(name: String, livesLeft: Int)

      implicit val format: JsonFormat[Cat] = deriveFormat[Cat]

      checkRoundTrip(Cat("Oliver", 7), """{"livesLeft":7,"name":"Oliver"}""")
    }
  }
}
