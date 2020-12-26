# spray-json derived codecs

[![Build Status](https://travis-ci.com/paoloboni/spray-json-derived-codecs.svg?branch=master)](https://travis-ci.com/paoloboni/spray-json-derived-codecs)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/spray-json-derived-codecs_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/spray-json-derived-codecs_2.13)

A library to derive `JsonFormat[T]` instances for `T`.

The derivation currently supports:
- sum types
- product types
- recursive types
- polymorphic types

Undefined optional members are not rendered.

## Installation

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "spray-json-derived-codecs" % "<version>"
```

## Usage

### Product types

```scala
import spray.json._

case class Cat(name: String, livesLeft: Int)

object Test extends App with DefaultJsonProtocol with derived.Instances {
  val oliver: Cat = Cat("Oliver", 7)
  val encoded     = oliver.toJson
  
  assert(encoded == """{"livesLeft":7,"name":"Oliver"}""".parseJson)
  assert(encoded.convertTo[Cat] == oliver)
}
```

### Union types

```scala
import spray.json._

sealed trait Pet
case class Cat(name: String, livesLeft: Int)   extends Pet
case class Dog(name: String, bonesHidden: Int) extends Pet

object Test extends App with DefaultJsonProtocol with derived.Instances {
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

Union types are encoded by using a discriminator field, which by default is `type`.

The discriminator can be customised when needed, by annotating the top-level type with the `@Discriminator` annotation:

```scala
import spray.json._
import spray.json.derived.Discriminator

@Discriminator("petType")
sealed trait Pet
case class Cat(name: String, livesLeft: Int)   extends Pet
case class Dog(name: String, bonesHidden: Int) extends Pet

object Test extends App with DefaultJsonProtocol with derived.Instances {
  val oliver: Pet   = Cat("Oliver", 7)
  val encodedOliver = oliver.toJson
  assert(encodedOliver == """{"livesLeft":7,"name":"Oliver","petType":"Cat"}""".parseJson)
  assert(encodedOliver.convertTo[Pet] == oliver)
}
```

## License

spray-json-derived-codecs is licensed under [APL 2.0](http://www.apache.org/licenses/LICENSE-2.0).
