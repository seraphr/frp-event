package jp.seraphr.bot.chat

import jp.seraphr.bot.core.BotSystemComponent

/**
 *
 */
trait ChatSystemComponent extends BotSystemComponent {
  val chatSystem: ChatSystem

  trait ChatSystem {

  }
}