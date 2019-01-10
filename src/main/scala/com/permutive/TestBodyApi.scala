package com.permutive

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class TestBodyApi(foo: String)
object TestBodyApi {
  implicit val decoder: Decoder[TestBodyApi] = deriveDecoder
  implicit val encoder: Encoder[TestBodyApi] = deriveEncoder
}
