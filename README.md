# spray-json derived codecs

[![Build Status](https://travis-ci.com/paoloboni/spray-json-derived-codecs.svg?branch=master)](https://travis-ci.com/paoloboni/spray-json-derived-codecs)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/spray-json-derived-codecs_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/spray-json-derived-codecs_2.13)
[![codecov.io](http://codecov.io/github/paoloboni/spray-json-derived-codecs/coverage.svg?branch=master)](http://codecov.io/github/paoloboni/spray-json-derived-codecs?branch=master)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A library to derive `JsonFormat[T]` instances for `T`.

The derivation currently supports:
- sum types
- product types
- recursive types
- polymorphic types

## Installation

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "spray-json-derived-codecs" % "2.2.0"
```

## Usage

For automatic derivation, add the following import:

```scala
import spray.json.derived.auto._
```

If you prefer to explicitly define your formats, then you can use semi-auto derivation:

```scala
import spray.json.derived.semiauto._
```

### Examples

#### Product types

##### Auto derivation

```scala
import spray.json._
import spray.json.derived.auto._

case class Cat(name: String, livesLeft: Int)

object Test extends App with DefaultJsonProtocol {
  val oliver: Cat = Cat("Oliver", 7)
  val encoded     = oliver.toJson
  
  assert(encoded == """{"livesLeft":7,"name":"Oliver"}""".parseJson)
  assert(encoded.convertTo[Cat] == oliver)
}
```

##### Semi-auto derivation

```scala
import spray.json._
import spray.json.derived.semiauto._

case class Cat(name: String, livesLeft: Int)

object Test extends App with DefaultJsonProtocol {
  implicit val format: JsonFormat[Cat] = deriveFormat[Cat]

  val oliver: Cat = Cat("Oliver", 7)
  val encoded     = oliver.toJson

  assert(encoded == """{"livesLeft":7,"name":"Oliver"}""".parseJson)
  assert(encoded.convertTo[Cat] == oliver)
}
```

#### Union types

Union types are encoded by using a discriminator field, which by default is `type`.

```scala
import spray.json._
import spray.json.derived.auto._

sealed trait Pet
case class Cat(name: String, livesLeft: Int)   extends Pet
case class Dog(name: String, bonesHidden: Int) extends Pet

object Test extends App with DefaultJsonProtocol {
  val oliver: Pet   = Cat("Oliver", 7)
  val encodedOliver = oliver.toJson
  assert(encodedOliver == """{"livesLeft":7,"name":"Oliver","type":"Cat"}""".parseJson)
  assert(encodedOliver.convertTo[Pet] == oliver)

  val albert: Pet   = Dog("Albert", 3)
  val encodedAlbert = albert.toJson
  assert(encodedAlbert == """{"bonesHidden":3,"name":"Albert","type":"Dog"}""".parseJson)
  assert(encodedAlbert.convertTo[Pet] == albert)
}
```

The discriminator can be customised by annotating the union type with the `@Discriminator` annotation:

```scala
import spray.json._
import spray.json.derived.auto._
import spray.json.derived.Discriminator

@Discriminator("petType")
sealed trait Pet
case class Cat(name: String, livesLeft: Int)   extends Pet
case class Dog(name: String, bonesHidden: Int) extends Pet

object Test extends App with DefaultJsonProtocol {
  val oliver: Pet   = Cat("Oliver", 7)
  val encodedOliver = oliver.toJson
  assert(encodedOliver == """{"livesLeft":7,"name":"Oliver","petType":"Cat"}""".parseJson)
  assert(encodedOliver.convertTo[Pet] == oliver)
}
```

#### Recursive types

```scala
import spray.json._
import spray.json.derived.auto._

sealed trait Tree
case class Leaf(s: String)            extends Tree
case class Node(lhs: Tree, rhs: Tree) extends Tree

object Test extends App with DefaultJsonProtocol {

  val obj: Tree = Node(Node(Leaf("1"), Leaf("2")), Leaf("3"))
  val encoded   = obj.toJson
  val expectedJson =
    """{
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
  assert(encoded == expectedJson.parseJson)
  assert(encoded.convertTo[Tree] == obj)
}
```

#### Polymorphic types

```scala
import spray.json._
import spray.json.derived.auto._

case class Container[T](value: T)

object Test extends App with DefaultJsonProtocol {

  val cString: Container[String] = Container("abc")
  val cStringEncoded             = cString.toJson
  assert(cStringEncoded == """{"value":"abc"}""".parseJson)
  assert(cStringEncoded.convertTo[Container[String]] == cString)

  val cInt: Container[Int] = Container(123)
  val cIntEncoded          = cInt.toJson
  assert(cIntEncoded == """{"value":123}""".parseJson)
  assert(cIntEncoded.convertTo[Container[Int]] == cInt)
}
```

#### Undefined optional members

By default, undefined optional members are not rendered:

```scala
import spray.json._
import spray.json.derived.auto._

case class Dog(toy: Option[String])

object Test extends App with DefaultJsonProtocol {
  val aDog        = Dog(toy = None)
  val aDogEncoded = aDog.toJson
  assert(aDogEncoded.compactPrint == "{}")
}
```

It's possible to render undefined optional members as null values by specifying an alternative configuration.
Use the mixin `derived.WithConfiguration` and specify the alternative configuration as implicit value:

```scala
import spray.json._
import spray.json.derived.auto._
import spray.json.derived.Configuration

case class Dog(toy: Option[String])

object Test extends App with DefaultJsonProtocol with derived.WithConfiguration {

  implicit val conf: Configuration = Configuration(renderNullOptions = true)

  val aDog        = Dog(toy = None)
  val aDogEncoded = aDog.toJson
  assert(aDogEncoded.compactPrint == """{"toy":null}""")
}
```

## License

spray-json-derived-codecs is licensed under [APL 2.0](http://www.apache.org/licenses/LICENSE-2.0).
