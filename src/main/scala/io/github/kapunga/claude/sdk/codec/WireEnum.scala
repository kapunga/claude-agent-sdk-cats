package io.github.kapunga.claude.sdk.codec

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

/** Trait for enums whose cases carry a wire-format string value. */
trait WireEnum:
  def wireValue: String

object WireEnum:

  /** Base companion for WireEnum types. Provides circe codecs derived from wireValue. */
  abstract class Companion[E <: WireEnum](values: => Array[E]):
    private lazy val byWire: Map[String, E] = values.map(e => e.wireValue -> e).toMap

    def fromWireValue(s: String): Option[E] = byWire.get(s)

    given Encoder[E] = Encoder[String].contramap(_.wireValue)

    given Decoder[E] = Decoder[String].emap { s =>
      fromWireValue(s).toRight(s"Unknown ${getClass.getSimpleName.stripSuffix("$")} value: $s")
    }

    given KeyEncoder[E] = KeyEncoder[String].contramap(_.wireValue)

    given KeyDecoder[E] = KeyDecoder.instance(fromWireValue)
