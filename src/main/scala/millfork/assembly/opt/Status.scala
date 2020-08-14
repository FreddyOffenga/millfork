package millfork.assembly.opt

import millfork.assembly.mos.opt.SourceOfNZ

/**
  * @author Karol Stasiak
  */


sealed trait Status[+T] {

  def |[U >: T](value: => Status[U]): Status[U] = this match {
    case AnyStatus | UnknownStatus => value
    case x => x
  }

  def contains[U >: T](value: U): Boolean

  def ~[U >: T](that: Status[U]): Status[U] = {
    (this, that) match {
      case (AnyStatus, _) => AnyStatus
      case (_, AnyStatus) => AnyStatus
      case (SingleStatus(x), SingleStatus(y)) => if (x == y) SingleStatus(x) else AnyStatus
      case (SingleStatus(x), UnknownStatus) => SingleStatus(x)
      case (UnknownStatus, SingleStatus(x)) => SingleStatus(x)
      case (UnknownStatus, UnknownStatus) => UnknownStatus
    }
  }

  def <*>[U, V](that: Status[U])(f: (T,U) => V): Status[V] = (this, that) match {
    case (SingleStatus(t), SingleStatus(u)) => SingleStatus(f(t, u))
    case (UnknownStatus, UnknownStatus) => UnknownStatus
    case _ => AnyStatus
  }

  def map[U](f: T => U): Status[U] = this match {
    case SingleStatus(x) => SingleStatus(f(x))
    case _ => AnyStatus
  }

  def flatMap[U](f: T => Status[U]): Status[U] = this match {
    case SingleStatus(x) => f(x)
    case _ => AnyStatus
  }

  def exists(predicate: T => Boolean): Boolean = this match {
    case AnyStatus | UnknownStatus => false
    case SingleStatus(x) => predicate(x)
  }
}

case class SingleStatus[T](t: T) extends Status[T] {
  override def contains[U >: T](value: U): Boolean = t == value

  override def toString: String = t match {
    case true => "1"
    case false => "0"
    case _ => t.toString
  }
}

case object UnknownStatus extends Status[Nothing] {
  override def contains[U >: Nothing](value: U) = false

  override def toString: String = "_"
}

case object AnyStatus extends Status[Nothing] {
  override def contains[U >: Nothing](value: U) = false

  override def toString: String = "#"
}

object Status {

  val SingleTrue: Status[Boolean] = SingleStatus(true)
  val SingleFalse: Status[Boolean] = SingleStatus(false)
  val SingleZero: Status[Int] = SingleStatus(0)
  val SingleFF: Status[Int] = SingleStatus(0xff)

  @inline
  private def wrapBool(b: Boolean): Status[Boolean] = if (b) SingleTrue else SingleFalse

  def flatMap2[T, U, R](a: Status[T], b: Status[U])(f: (T, U) => Status[R]): Status[R] = (a, b) match {
    case (SingleStatus(t), SingleStatus(u)) => f(t, u)
    case (UnknownStatus, UnknownStatus) => UnknownStatus
    case _ => AnyStatus
  }

  def flatMap3[T, U, V, R](a: Status[T], b: Status[U], c: Status[V])(f: (T, U, V) => Status[R]): Status[R] = (a, b, c) match {
    case (SingleStatus(t), SingleStatus(u), SingleStatus(v)) => f(t, u, v)
    case (UnknownStatus, UnknownStatus, UnknownStatus) => UnknownStatus
    case _ => AnyStatus
  }

  def flatMap4[T, U, V, W, R](a: Status[T], b: Status[U], c: Status[V], d: Status[W])(f: (T, U, V, W) => Status[R]): Status[R] = (a, b, c, d) match {
    case (SingleStatus(t), SingleStatus(u), SingleStatus(v), SingleStatus(w)) => f(t, u, v, w)
    case (UnknownStatus, UnknownStatus, UnknownStatus, UnknownStatus) => UnknownStatus
    case _ => AnyStatus
  }

  implicit class BoolStatusOps(val inner: Status[Boolean]) extends AnyVal {
    def withHiddenHi: Status[Boolean] = inner match {
      case SingleStatus(false) => inner
      case _ => AnyStatus
    }
    def negate: Status[Boolean] = inner match {
      case SingleStatus(x) => wrapBool(!x)
      case x => x
    }
  }
  implicit class SourceOfNZStatusOps(val inner: Status[SourceOfNZ]) extends AnyVal {
    def isFromA: Boolean = inner match {
      case SingleStatus(v) => v.a
      case _ => false
    }
    def isFromAW: Boolean = inner match {
      case SingleStatus(v) => v.aw
      case _ => false
    }
    def isFromX: Boolean = inner match {
      case SingleStatus(v) => v.x
      case _ => false
    }
    def isFromY: Boolean = inner match {
      case SingleStatus(v) => v.y
      case _ => false
    }
    def isFromIZ: Boolean = inner match {
      case SingleStatus(v) => v.iz
      case _ => false
    }
    def butNotA: Status[SourceOfNZ] = inner match {
      case SingleStatus(v) => if (v.a || v.aw) AnyStatus else inner
      case _ => inner
    }
    def butNotX: Status[SourceOfNZ] = inner match {
      case SingleStatus(v) => if (v.x) AnyStatus else inner
      case _ => inner
    }
    def butNotY: Status[SourceOfNZ] = inner match {
      case SingleStatus(v) => if (v.y) AnyStatus else inner
      case _ => inner
    }
  }
  implicit class IntStatusOps(val inner: Status[Int]) extends AnyVal {

    def bit0: Status[Boolean] = inner match {
      case SingleStatus(x) => SingleStatus((x & 1) != 0)
      case _ => AnyStatus
    }

    def bit7: Status[Boolean] = inner match {
      case SingleStatus(x) => SingleStatus((x & 0x80) != 0)
      case _ => AnyStatus
    }

    def z(f: Int => Int = identity): Status[Boolean] = inner match {
      case SingleStatus(x) =>
        val y = f(x) & 0xff
        wrapBool(y == 0)
      case _ => AnyStatus
    }

    def n(f: Int => Int = identity): Status[Boolean] = inner match {
      case SingleStatus(x) =>
        val y = f(x) & 0xff
        wrapBool(y >= 0x80)
      case _ => AnyStatus
    }

    def zw(f: Int => Int = identity): Status[Boolean] = inner match {
      case SingleStatus(x) =>
        val y = f(x) & 0xffff
        wrapBool(y == 0)
      case _ => AnyStatus
    }

    def nw(f: Int => Int = identity): Status[Boolean] = inner match {
      case SingleStatus(x) =>
        val y = f(x) & 0xffff
        wrapBool(y >= 0x8000)
      case _ => AnyStatus
    }

    def lo: Status[Int] = inner match {
      case SingleStatus(x) => SingleStatus(x & 0xff)
      case _ => AnyStatus
    }

    def hi: Status[Int] = inner match {
      case SingleStatus(x) => SingleStatus(x.&(0xff00).>>(8))
      case _ => AnyStatus
    }

    def adc(value: Int, carry: Status[Boolean], decimal: Status[Boolean]): (Status[Int], Status[Boolean]) = inner match {
      case SingleStatus(x) => decimal match {
        case SingleStatus(false) => carry match {
          case SingleStatus(true) => SingleStatus((x + value + 1) & 0xff) -> SingleStatus((x.&(0xff) + value.&(0xff) + 1) > 0xff)
          case SingleStatus(false) => SingleStatus((x + value) & 0xff) -> SingleStatus((x.&(0xff) + value.&(0xff)) > 0xff)
          case _ => AnyStatus -> (if (value == 0) SingleFalse else AnyStatus)
        }
        case _ => AnyStatus -> AnyStatus
      }
      case _ => AnyStatus -> AnyStatus
    }

    def adc(value: Status[Int], carry: Status[Boolean]): (Status[Int], Status[Boolean]) = (inner, value, carry) match {
      case (SingleStatus(x), SingleStatus(y), SingleStatus(false)) =>
        SingleStatus((x + y) & 0xff) -> SingleStatus((x.&(0xff) + y.&(0xff)) > 0xff)
      case (SingleStatus(x), SingleStatus(y), SingleStatus(true)) =>
        SingleStatus((x + y + 1) & 0xff) -> SingleStatus((x.&(0xff) + y.&(0xff) + 1) > 0xff)
      case (SingleStatus(x), SingleStatus(y), AnyStatus) => x + y match {
        case 255 => AnyStatus -> AnyStatus
        case z if z < 255 => AnyStatus -> SingleFalse
        case z if z > 255 => AnyStatus -> SingleTrue
      }
      case (SingleStatus(0), AnyStatus, SingleStatus(false))
           | (AnyStatus, SingleStatus(0), SingleStatus(false)) => AnyStatus -> SingleFalse
      case (SingleStatus(0xff), AnyStatus, SingleStatus(true))
           | (AnyStatus, SingleStatus(0xff), SingleStatus(true)) => AnyStatus -> SingleTrue
      case _ => AnyStatus -> AnyStatus
    }

    def sbc(value: Int, carry: Status[Boolean], decimal: Status[Boolean]): (Status[Int], Status[Boolean]) = inner match {
      case SingleStatus(x) => decimal match {
        case SingleStatus(false) => carry match {
          case SingleStatus(true) => SingleStatus((x - value) & 0xff) -> SingleStatus((x.&(0xff) - value.&(0xff)) >= 0)
          case SingleStatus(false) => SingleStatus((x - value - 1) & 0xff) -> SingleStatus((x.&(0xff) - value.&(0xff) - 1) >= 0)
          case _ => AnyStatus -> (if (value == 0) SingleTrue else AnyStatus)
        }
        case _ => AnyStatus -> AnyStatus
      }
      case _ => AnyStatus -> AnyStatus
    }

    def sbb(value: Status[Int], carry: Status[Boolean]): (Status[Int], Status[Boolean]) = (inner, value) match {
      case (SingleStatus(x), SingleStatus(v)) => carry match {
        case SingleStatus(false) => SingleStatus((x - v) & 0xff) -> SingleStatus((x.&(0xff) - v.&(0xff)) < 0)
        case SingleStatus(true) => SingleStatus((x - v - 1) & 0xff) -> SingleStatus((x.&(0xff) - v.&(0xff) - 1) < 0)
        case _ => AnyStatus -> (if (v == 0) SingleFalse else AnyStatus)
      }
      case (_, SingleStatus(0)) => carry match {
        case SingleStatus(false) => inner -> SingleFalse
        case _ => AnyStatus -> AnyStatus
      }
      case _ => AnyStatus -> AnyStatus
    }

    def adc_w(value: Int, carry: Status[Boolean], decimal: Status[Boolean]): Status[Int] = inner match {
      case SingleStatus(x) => decimal match {
        case SingleStatus(false) => carry match {
          case SingleStatus(true) => SingleStatus((x + value + 1) & 0xffff)
          case SingleStatus(false) => SingleStatus((x + value) & 0xffff)
          case _ => AnyStatus
        }
        case _ => AnyStatus
      }
      case _ => AnyStatus
    }
  }

}