package com.github.volkerstampa.eventuate

/**
  * Takes a [[java.util.Formatter]] like string to format a `case class`-instance.
  *
  * The format specifiers within the format string must be named according to the fields of the
  * `case class` to be formatted. For this the leading `%` of a format specifier is followed by a
  * field-name in parenthesis (similar to the mapping key in python's format-syntax):
  *
  * {{{
  *   case class A(fieldA: String, fieldB: Int)
  *
  *   new CaseClassFormatter[A]("A: %(fieldA)-5s, B: %(fieldB)03d").format(A("Hi", 42))
  *   // "A: Hi    B: 042"
  * }}}
  *
  * A format specifier may also be named `this`. In this case the entire `case class` instance is
  * provided as argument instead of a single field.
  *
  * @tparam A Must be a `case class`. The implementation relies on the fact that
  *           A's `productIterator` returns the values of A's _fields_ in the same order as
  *           `getDeclaredFields` of A's class.
  */
class CaseClassFormatter[A <: Product](extendedFormatString: String) {

  private val FieldNameGroup = "fieldName"

  private val fieldNameRegEx = """\%\((\p{Alnum}+)\)""".r(FieldNameGroup)

  private val plainFormatString: String =
    fieldNameRegEx.replaceAllIn(extendedFormatString, "%")

  def format(arg: A): String = plainFormatString.format(toFormatArguments(arg): _*)

  private def toFormatArguments(arg: A): Seq[Any] = {
    val fieldValueMap = arg.getClass.getDeclaredFields.map(_.getName).zip(arg.productIterator.to).toMap.withDefault {
      case "this" => arg
      case fieldName => s"[unknown field: $fieldName]"
    }
    fieldNameRegEx.findAllMatchIn(extendedFormatString).map(_.group(FieldNameGroup)).map(fieldValueMap).toList
  }
}
