package jp.seraphr.bot.core

trait BotSystemComponent {
  case class User(id: String, name: String)

  type _BotSystem <: BotSystem
  trait BotSystem {

  }
}