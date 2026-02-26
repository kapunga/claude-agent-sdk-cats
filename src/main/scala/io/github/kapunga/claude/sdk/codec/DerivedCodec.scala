package io.github.kapunga.claude.sdk.codec

import io.circe.Encoder

object DerivedCodec:
  inline def encoderDropNulls[A](using inline m: scala.deriving.Mirror.ProductOf[A]): Encoder[A] =
    Encoder.AsObject.derived[A].mapJson(_.dropNullValues)
