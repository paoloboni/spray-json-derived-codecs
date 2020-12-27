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

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import spray.json._
import spray.json.derived.semiauto._

class SemiAutoDerivationSpec extends AnyFeatureSpec with Matchers with CheckRoundTrip with DefaultJsonProtocol {
  Feature("semi-automatic derivation") {
    Scenario("explicitly define formats") {
      case class Cat(name: String, livesLeft: Int)

      implicit val format: JsonFormat[Cat] = deriveFormat[Cat]

      checkRoundTrip(Cat("Oliver", 7), """{"livesLeft":7,"name":"Oliver"}""")
    }
  }
}
