package app.pitchback.web

object AiStarters {
    fun forKind(kind: String, lang: AppLang): List<String> = when (kind) {
        "pitchBack" -> pitchBack(lang)
        "intro" -> intro(lang)
        "final" -> finalMsg(lang)
        "location" -> location(lang)
        "noShow" -> noShow(lang)
        else -> pitch(lang)
    }

    private fun pitch(lang: AppLang) = list(
        lang,
        "What belief about yourself didn't survive Saigon?" to "Niềm tin nào về bản thân không còn đứng vững sau khi đến Sài Gòn?",
        "When did you last feel truly seen by a stranger?" to "Lần cuối bạn cảm thấy được một người lạ nhìn thấu là khi nào?",
        "How do you stay kind when the city is loud and impatient?" to "Làm sao bạn giữ được sự tử tế khi thành phố ồn ào và nôn nóng?",
        "What does home mean after you've left one behind?" to "Nhà nghĩa là gì sau khi bạn đã rời một nhà?",
        "Which part of you only comes out after midnight here?" to "Phần nào của bạn chỉ hiện ra sau nửa đêm ở đây?",
        "What are you grieving that nobody knows about?" to "Điều gì bạn đang để tang mà không ai biết?",
        "How has money quietly changed the way you love?" to "Tiền đã âm thầm đổi cách bạn yêu như thế nào?",
        "What conversation are you afraid to start, and why now?" to "Cuộc trò chuyện nào bạn sợ mở lời, và vì sao là lúc này?",
        "Where do you go when you need to remember who you were?" to "Bạn đi đâu khi cần nhớ mình từng là ai?",
        "What would you tell your first-week-in-Saigon self?" to "Bạn sẽ nói gì với phiên bản tuần đầu ở Sài Gòn?",
        "How do you decide who gets the honest version of you?" to "Bạn chọn ai được gặp phiên bản thật của mình?",
        "What did you come here to become, and is it still true?" to "Bạn đến đây để trở thành gì, và điều đó còn đúng không?",
        "When did ambition start costing you tenderness?" to "Khi nào tham vọng bắt đầu lấy đi sự dịu dàng?",
        "What does loyalty look like in a city of temporary people?" to "Lòng trung thành trông thế nào trong thành phố đầy người tạm?",
        "How do you pray, or not, after everything that's happened?" to "Sau tất cả những gì đã xảy ra, bạn còn cầu nguyện — hay không?",
        "What silence in your life is asking to be spoken?" to "Sự im lặng nào trong đời bạn đang đòi được nói ra?",
        "Who are you when nobody needs anything from you?" to "Bạn là ai khi không ai cần gì từ bạn?",
        "What did the last heartbreak teach you about staying?" to "Tan vỡ lần trước dạy bạn điều gì về việc ở lại?",
        "How do you hold two cultures without splitting in half?" to "Làm sao ôm hai nền văn hóa mà không bị xé đôi?",
        "What question should we sit with until the coffee goes cold?" to "Câu hỏi nào chúng ta nên ngồi cùng đến khi cà phê nguội?",
    )

    private fun pitchBack(lang: AppLang) = list(
        lang,
        "Your question about belonging has been sitting with me." to "Câu hỏi về thuộc về của bạn cứ ở lại trong tôi.",
        "I want to meet the person who wrote this, not just agree." to "Tôi muốn gặp người viết những dòng này, không chỉ gật đầu.",
        "I've been circling a similar honesty and I'd rather not do it alone." to "Tôi cũng đang đi vòng quanh một sự thật giống vậy, và không muốn đi một mình.",
        "This is the conversation I've been too careful to start." to "Đây là cuộc trò chuyện tôi quá cẩn thận nên chưa dám mở.",
        "I can bring a quiet hour and the truth I'm still naming." to "Tôi có thể mang một giờ yên và sự thật mình còn đang đặt tên.",
        "Your line about home stopped me on the street." to "Câu về nhà của bạn khiến tôi đứng lại giữa phố.",
        "I'd rather sit with the hard part than keep it polished." to "Tôi muốn ngồi với phần khó hơn là giữ cho nó bóng bẩy.",
        "I know that grief you're pointing at, in my own dialect." to "Tôi biết nỗi buồn bạn đang chỉ — bằng phương ngữ của mình.",
        "Let's talk about what we're becoming, not what we post." to "Hãy nói về điều chúng ta đang trở thành, không phải điều chúng ta đăng.",
        "I'm looking for a mind that doesn't flinch from depth." to "Tôi tìm một tâm trí không né chiều sâu.",
        "Your pitch felt like someone leaving the door unlocked." to "Lời ngỏ của bạn như ai đó cố ý để cửa không khóa.",
        "I want to ask you the question under the question." to "Tôi muốn hỏi câu nằm dưới câu hỏi.",
        "I've changed my mind about this city lately. Have you?" to "Dạo này tôi đổi ý về thành phố này. Còn bạn?",
        "If we meet, I hope we skip small talk on purpose." to "Nếu gặp, tôi mong chúng ta cố ý bỏ chuyện xã giao.",
        "I can walk slowly and stay with whatever is true that day." to "Tôi có thể đi chậm và ở lại với điều thật của ngày đó.",
        "Your honesty about ambition is the door I wanted." to "Sự thật về tham vọng của bạn là cánh cửa tôi đang tìm.",
        "I'd like to compare notes on leaving without disappearing." to "Tôi muốn đối chiếu cách rời đi mà không biến mất.",
        "There's a tenderness in this I don't want to waste on chat." to "Có sự dịu dàng ở đây mà tôi không muốn phí vào tin nhắn.",
        "I think we might be asking the same thing in different rooms." to "Tôi nghĩ chúng ta đang hỏi cùng một điều, ở những phòng khác nhau.",
        "Meet me where the conversation gets inconvenient." to "Gặp tôi ở chỗ cuộc trò chuyện bắt đầu bất tiện.",
    )

    private fun intro(lang: AppLang) = list(
        lang,
        "I come looking for conversations that linger after we stand up." to "Tôi đi tìm những cuộc trò chuyện còn vương sau khi đứng dậy.",
        "I live between languages and I'm tired of performing either one." to "Tôi sống giữa các ngôn ngữ và mệt vì phải diễn cả hai.",
        "I care more about how you think than where you're from." to "Tôi quan tâm cách bạn nghĩ hơn là bạn đến từ đâu.",
        "I'm rebuilding a quieter life in a loud city." to "Tôi đang dựng lại một đời sống yên hơn trong thành phố ồn.",
        "I notice people the way some people notice architecture." to "Tôi để ý người như người khác để ý kiến trúc.",
        "I've learned more from strangers' silences than from speeches." to "Tôi học từ sự im lặng của người lạ nhiều hơn từ bài nói.",
        "I'm here for depth, not a networking version of myself." to "Tôi đến vì chiều sâu, không phải bản mạng lưới của mình.",
        "I still don't know if Saigon is home or a long question." to "Tôi vẫn chưa biết Sài Gòn là nhà hay một câu hỏi dài.",
        "I keep a small circle and I choose it slowly." to "Tôi giữ một vòng tròn nhỏ và chọn nó chậm.",
        "I want friends who can sit with doubt without fixing it." to "Tôi muốn bạn bè ngồi được với nghi ngờ mà không vội chữa.",
        "I'm kinder in person than I am when I'm rushing." to "Tôi tử tế hơn khi gặp mặt so với khi đang vội.",
        "I left a version of success and I'm still naming the next one." to "Tôi đã rời một phiên bản thành công và còn đang đặt tên cái tiếp.",
        "I walk to think, and I think better with another honest mind." to "Tôi đi bộ để nghĩ, và nghĩ hay hơn với một tâm trí thật.",
        "I'm interested in the ethics of how we treat each other here." to "Tôi quan tâm đạo đức trong cách mình đối xử với nhau ở đây.",
        "I don't need to be impressive. I need to be accurate." to "Tôi không cần gây ấn tượng. Tôi cần cho đúng.",
        "I've been loved poorly and I'm learning a slower kind." to "Tôi từng được yêu vụng và đang học một cách chậm hơn.",
        "I belong partly to this city and partly to the one I left." to "Tôi thuộc một phần thành phố này và một phần nơi đã rời.",
        "I ask better questions than I give answers." to "Tôi hỏi hay hơn là trả lời.",
        "I'm looking for people who still change their minds." to "Tôi tìm những người vẫn còn đổi ý được.",
        "If we meet, bring the chapter you're actually in." to "Nếu gặp, hãy mang chương bạn đang sống thật.",
    )

    private fun finalMsg(lang: AppLang) = list(
        lang,
        "Thank you for the honesty you brought to the table." to "Cảm ơn sự thật lòng bạn đã mang tới bàn.",
        "I felt less alone in the questions I usually carry quietly." to "Tôi bớt cô đơn với những câu hỏi mình thường mang thầm.",
        "You met me where I actually was, not where I perform." to "Bạn gặp tôi ở chỗ tôi đang là, không phải chỗ tôi diễn.",
        "I'll keep sitting with what you said after I walk away." to "Tôi sẽ còn ngồi với điều bạn nói sau khi bước đi.",
        "This was the kind of afternoon that rearranges a week." to "Đây là buổi chiều làm xáo một tuần.",
        "I'm grateful we didn't rush past the difficult part." to "Tôi biết ơn vì chúng ta không vội bỏ qua phần khó.",
        "You reminded me that strangers can still be precise with care." to "Bạn nhắc tôi rằng người lạ vẫn có thể tử tế một cách chính xác.",
        "I leave with more language for something I couldn't name." to "Tôi ra về với nhiều lời hơn cho điều trước đây không gọi được.",
        "May the rest of your week be as true as this conversation." to "Mong phần còn lại của tuần bạn thật như cuộc trò chuyện này.",
        "If our paths cross again, I hope we skip the small talk still." to "Nếu lại gặp, mong chúng ta vẫn bỏ chuyện xã giao.",
        "Thank you for trusting me with the unpolished version." to "Cảm ơn vì tin tôi với phiên bản chưa được đánh bóng.",
        "I felt respect in how carefully you listened." to "Tôi thấy sự tôn trọng trong cách bạn lắng nghe kỹ.",
        "This city feels a little less anonymous tonight." to "Đêm nay thành phố bớt vô danh một chút.",
        "I'll remember the pause before you answered." to "Tôi sẽ nhớ khoảng lặng trước khi bạn trả lời.",
        "You made room for complexity. That is rare and I noticed." to "Bạn chừa chỗ cho sự phức tạp. Hiếm, và tôi để ý.",
        "Walk home slowly — some of this deserves to settle." to "Về nhà chậm thôi — có thứ cần được lắng.",
        "I'm glad we chose a real conversation over a convenient one." to "Tôi mừng vì ta chọn cuộc trò chuyện thật hơn là cuộc tiện.",
        "Take care of the part of you that spoke today." to "Hãy giữ gìn phần bạn đã lên tiếng hôm nay.",
        "Whatever we named, I hope it stays kind to you." to "Dù ta đã gọi tên điều gì, mong nó vẫn tử tế với bạn.",
        "Until another table, another honest hour." to "Hẹn một bàn khác, một giờ thật khác.",
    )

    private fun location(lang: AppLang) = list(
        lang,
        "A room quiet enough that people tell the truth." to "Một chỗ đủ yên để người ta nói thật.",
        "Where the light is kind and nobody is performing brunch." to "Nơi ánh sáng dịu và không ai đang diễn buổi brunch.",
        "A corner for long silences that aren't awkward." to "Một góc cho những im lặng dài mà không ngượng.",
        "The kind of table where two strangers can become specific." to "Kiểu bàn để hai người lạ trở nên cụ thể.",
        "Soft chairs, slow service, and space to change your mind." to "Ghế êm, phục vụ chậm, và chỗ để đổi ý.",
        "A place that doesn't punish you for staying two hours." to "Nơi không phạt bạn vì ngồi hai tiếng.",
        "Good for grief, books, and conversations that fork." to "Hợp để mang nỗi buồn, sách, và chuyện rẽ nhánh.",
        "Outside noise, inside hush — a pocket in the city." to "Ngoài ồn, trong lặng — một túi nhỏ giữa phố.",
        "Where you can hear yourself decide something." to "Nơi bạn nghe được mình quyết một điều.",
        "Not a date set: a thinking set, with decent coffee." to "Không phải chỗ hẹn hò: chỗ để nghĩ, cà phê tử tế.",
        "A courtyard that makes ambition sit down for a minute." to "Sân trong khiến tham vọng phải ngồi xuống một lúc.",
        "Dim enough to be honest, bright enough to read a face." to "Đủ tối để thật, đủ sáng để đọc một gương mặt.",
        "The staff leave you alone in the best way." to "Nhân viên để bạn yên theo cách tốt nhất.",
        "A table by the window for talking about leaving and staying." to "Bàn cạnh cửa sổ để nói về đi và ở.",
        "Somewhere the city thins out and people thicken." to "Nơi thành phố mỏng lại và con người đậm hơn.",
        "For people who come to listen, not to be seen." to "Cho người đến để nghe, không phải để được nhìn.",
        "A slow room with a view that doesn't demand a photo." to "Một phòng chậm với khung hình không đòi chụp ảnh.",
        "Close enough to walk, far enough to feel chosen." to "Đủ gần để đi bộ, đủ xa để thấy là đã chọn.",
        "Where arguments can stay gentle and still be real." to "Nơi tranh luận vẫn dịu mà vẫn thật.",
        "A shelter for the kind of talk that needs a second cup." to "Chỗ trú cho chuyện cần thêm một ly nữa.",
    )

    private fun noShow(lang: AppLang) = list(
        lang,
        "They never arrived and didn't message." to "Họ không đến và không nhắn gì.",
        "I waited past the time we agreed and left." to "Tôi đợi quá giờ hẹn rồi ra về.",
        "They canceled only after I was already there." to "Họ hủy khi tôi đã có mặt.",
        "No reply after I confirmed I was at the place." to "Không trả lời sau khi tôi báo đã tới.",
        "They asked to move the time, then disappeared." to "Họ xin dời giờ rồi biến mất.",
        "I saw them nearby but they didn't come in." to "Tôi thấy họ gần đó nhưng không vào.",
        "The wait felt disrespectful, not just late." to "Việc đợi không chỉ muộn — mà thiếu tôn trọng.",
        "I want this on record so the next person is safer." to "Tôi muốn ghi lại để người sau được an toàn hơn.",
    )

    private fun list(lang: AppLang, vararg pairs: Pair<String, String>): List<String> =
        pairs.map { if (lang == AppLang.VI) it.second else it.first }
}
