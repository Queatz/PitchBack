package app.pitchback.web

enum class AppLang { EN, VI }

class T(val lang: AppLang) {
    private fun s(en: String, vi: String) = if (lang == AppLang.VI) vi else en

    val appName get() = "ranh.app"
    val pitches get() = s("Pitches", "Lời ngỏ")
    val inbox get() = s("Inbox", "Hộp thư")
    val me get() = s("Me", "Tôi")
    val admin get() = s("Admin", "Quản trị")
    val pitchBack get() = s("Pitch back", "Ngỏ lời")
    val thePitch get() = s("The pitch", "Lời ngỏ")
    val theLocation get() = s("The location", "Địa điểm")
    val profileUpdated get() = s("Profile updated", "Đã cập nhật hồ sơ")
    val verifiedBadge get() = s("Verified", "Đã xác minh")
    val confirmDismissPitch get() = s(
        "Remove this pitch completely? This can't be undone.",
        "Xóa hẳn lời ngỏ này? Không hoàn tác được.",
    )
    val acceptPitch get() = s("Accept pitch", "Chấp nhận lời ngỏ")
    val dismiss get() = s("Dismiss", "Bỏ qua")
    val cancel get() = s("Cancel", "Hủy")
    val save get() = s("Save", "Lưu")
    val signIn get() = s("Sign in", "Đăng nhập")
    val signOut get() = s("Sign out", "Đăng xuất")
    val register get() = s("Create account", "Tạo tài khoản")
    val name get() = s("Name", "Tên")
    val phone get() = s("Phone", "Số điện thoại")
    val password get() = s("Password", "Mật khẩu")
    val intro get() = s("Intro", "Giới thiệu")
    val language get() = s("Language", "Ngôn ngữ")
    val systemLang get() = s("Follow system", "Theo hệ thống")
    val english get() = s("English", "Tiếng Anh")
    val vietnamese get() = s("Vietnamese", "Tiếng Việt")
    val needAccount get() = s("Need an account? Register", "Chưa có tài khoản? Đăng ký")
    val haveAccount get() = s("Already have an account? Sign in", "Đã có tài khoản? Đăng nhập")
    val signInToPitch get() = s("Sign in to pitch back", "Đăng nhập để ngỏ lời")
    val signInInbox get() = s("Sign in to see pitch backs and meets.", "Đăng nhập để xem ngỏ lời và buổi hẹn.")
    val emptyFeed get() = s("No pitches yet. Be the first — or ask an admin to add a location.", "Chưa có lời ngỏ nào. Hãy tạo lời ngỏ đầu tiên — hoặc nhờ admin thêm địa điểm.")
    val emptyLocations get() = s("No locations yet. Ask an admin to add one.", "Chưa có địa điểm nào. Nhờ admin thêm một địa điểm.")
    val emptyInbox get() = s("Nothing waiting. Pitch back on a live pitch to start a meet.", "Chưa có gì. Ngỏ lời trên một lời ngỏ đang mở để bắt đầu buổi hẹn.")
    val enterPitch get() = s("Enter your pitch here...", "Nhập lời ngỏ của bạn...")
    val generateAi get() = s("Write with AI", "Viết bằng AI")
    val extraAi get() = s("Anything you want the AI to know?", "Bạn muốn AI biết thêm gì?")
    val insert get() = s("Insert", "Chèn")
    val replace get() = s("Replace", "Thay thế")
    val writing get() = s("Writing…", "Đang viết…")
    val generate get() = s("Generate", "Tạo")
    val myPitches get() = s("My pitches", "Lời ngỏ của tôi")
    val createPitch get() = s("Create a pitch", "Tạo lời ngỏ")
    val submitReview get() = s("Submit for review", "Gửi duyệt")
    val pending get() = s("Pending review", "Chờ duyệt")
    val pitchSubmitted get() = s(
        "Great! Your pitch will be visible when it passes review.",
        "Tuyệt! Lời ngỏ sẽ hiện khi được duyệt.",
    )
    val approved get() = s("Live", "Đang mở")
    val dismissed get() = s("Dismissed", "Đã từ chối")
    val remaining get() = s("Remaining pitch backs", "Lượt ngỏ lời còn")
    val totalRemainingPitches get() = s("Total remaining pitches", "Tổng lời ngỏ còn")
    val topic get() = s("Topic", "Chủ đề")
    val location get() = s("Location", "Địa điểm")
    val photos get() = s("Photos", "Ảnh")
    val photo get() = s("Photo", "Ảnh")
    val availableUntil get() = s("Available until (optional)", "Hết hạn (tuỳ chọn)")
    val allowSocial get() = s("Allow posting photos on socials after we meet", "Cho phép đăng ảnh lên mạng sau buổi gặp")
    val recommendPlace get() = s("Recommend a place", "Gợi ý địa điểm")
    val placeName get() = s("Place name", "Tên quán")
    val note get() = s("Note", "Ghi chú")
    val verify get() = s("Verify with Zalo phone", "Xác minh số Zalo")
    val verified get() = s("You are verified", "Bạn đã được xác minh")
    val verifyPending get() = s("A verification request is already pending", "Yêu cầu xác minh đang chờ duyệt")
    val verifySent get() = s("Verification request sent", "Đã gửi yêu cầu xác minh")
    val verificationRequested get() = s("Verification requested", "Đã gửi xác minh")
    val zaloPhone get() = s("Zalo-enabled phone", "Số điện thoại Zalo")
    val requestVerify get() = s("Request verification", "Gửi xác minh")
    val adminEntry get() = s("Admin desk", "Bàn quản trị")
    val incoming get() = s("Incoming pitch backs", "Ngỏ lời đến")
    val outgoing get() = s("Your pitch backs", "Ngỏ lời của bạn")
    val activeMeets get() = s("Active meets", "Buổi hẹn đang mở")
    val cancelledMeets get() = s("Canceled", "Đã hủy")
    val cancelMeet get() = s("Cancel meet", "Hủy buổi hẹn")
    val endMeet get() = s("Send final message & end", "Gửi tin nhắn cuối & kết thúc")
    val skipEnd get() = s("End without a message", "Kết thúc không nhắn")
    val finalMessage get() = s("Final message", "Tin nhắn cuối")
    val finalMessages get() = s("Final messages", "Tin nhắn cuối")
    val optional get() = s("Optional", "Tuỳ chọn")
    val weMet get() = s("We met!", "Chúng mình đã gặp!")
    val download get() = s("Download sticker", "Tải sticker")
    val noShow get() = s("Report no-show", "Báo không đến")
    val confirmNoShow get() = s("Report this as a no-show?", "Báo người kia không đến?")
    val noShowExplain get() = s("What happened? (optional)", "Chuyện gì xảy ra? (tuỳ chọn)")
    val reportedNoShow get() = s("No-show reported", "Đã báo không đến")
    val openNow get() = s("Open now", "Đang mở")
    val closedNow get() = s("Closed", "Đóng cửa")
    val expires get() = s("Expires", "Hết hạn")
    val yourPitch get() = s("Your pitch", "Lời ngỏ của bạn")
    val back get() = s("Back", "Quay lại")
    val loading get() = s("Loading…", "Đang tải…")
    val dropPhotos get() = s("Drop photos here, or tap to choose", "Thả ảnh vào đây, hoặc chạm để chọn")
    val dropPhoto get() = s("Drop a photo here, or tap to choose", "Thả một ảnh vào đây, hoặc chạm để chọn")
    val highlights get() = s("Highlights", "Điểm nổi bật")
    val address get() = s("Address", "Địa chỉ")
    val directions get() = s("Directions", "Chỉ đường")
    val hours get() = s("Opening hours", "Giờ mở cửa")
    val closed get() = s("Closed", "Đóng cửa")
    val description get() = s("Description", "Mô tả")
    val approve get() = s("Approve", "Duyệt")
    val users get() = s("Users", "Người dùng")
    val promote get() = s("Make admin", "Cấp admin")
    val disable get() = s("Disable", "Khóa")
    val enable get() = s("Enable", "Mở khóa")
    val locations get() = s("Locations", "Địa điểm")
    val recs get() = s("Place ideas", "Gợi ý quán")
    val placeForumIntro get() = s(
        "A quiet board for places worth meeting in. Suggest a café, park, or corner; an admin reviews it before it appears when people write a pitch.",
        "Một góc góp ý những nơi đáng để gặp nhau. Gợi ý quán, công viên, hay một góc phố; admin sẽ duyệt trước khi hiện khi mọi người viết lời ngỏ.",
    )
    val placeSubmitted get() = s("Thanks — we'll review this place.", "Cảm ơn — chúng mình sẽ xem địa điểm này.")
    val submitPlace get() = s("Submit place idea", "Gửi gợi ý địa điểm")
    val noShows get() = s("No-shows", "Vắng mặt")
    val review get() = s("Mark reviewed", "Đã xem")
    val addLocation get() = s("Add location", "Thêm địa điểm")
    val min80 get() = s("At least 80 characters", "Tối thiểu 80 ký tự")
    val min40 get() = s("At least 40 characters", "Tối thiểu 40 ký tự")
    val guestMe get() = s("Create an account to pitch and pitch back.", "Tạo tài khoản để đăng lời ngỏ và ngỏ lời.")
    val profilePhoto get() = s("Profile photo", "Ảnh đại diện")
    val cancelPitchBack get() = s("Cancel pitch back", "Hủy ngỏ lời")
    val close get() = s("Close", "Đóng")
    val open get() = s("Open", "Mở")
    val pickLocation get() = s("Pick a location", "Chọn địa điểm")
    val searchPlaces get() = s("Search places", "Tìm địa điểm")
    val chooseThisPlace get() = s("Meet here", "Gặp ở đây")
    val viewOnMap get() = s("View on map", "Xem bản đồ")
    val edit get() = s("Edit", "Sửa")
    val confirmDeleteLocation get() = s("Remove this location? Pitches using it may break.", "Xóa địa điểm này? Các lời ngỏ đang dùng nó có thể lỗi.")
    val somethingWrong get() = s("Something went wrong", "Có lỗi xảy ra")
    val mon get() = s("Mon", "T2")
    val tue get() = s("Tue", "T3")
    val wed get() = s("Wed", "T4")
    val thu get() = s("Thu", "T5")
    val fri get() = s("Fri", "T6")
    val sat get() = s("Sat", "T7")
    val sun get() = s("Sun", "CN")
    val meetTimes get() = s("When can you meet?", "Bạn rảnh lúc nào?")
    val meetTimesHint get() = s("Tap the days, then add the times you can meet.", "Chọn ngày, rồi thêm giờ bạn rảnh.")
    val addTime get() = s("Add a time", "Thêm giờ")
    val pickTimeSlot get() = s("Pick a time to meet", "Chọn giờ gặp mặt")
    val nextTimes get() = s("Next times", "Giờ gần nhất")
    val meetAt get() = s("Meet at", "Gặp tại")
    val meetOn get() = s("Meet on", "Gặp lúc")
    val noTimes get() = s("No upcoming times on this pitch", "Lời ngỏ này chưa có giờ trống")
    val months: List<String>
        get() = if (lang == AppLang.VI) {
            listOf("thg 1", "thg 2", "thg 3", "thg 4", "thg 5", "thg 6", "thg 7", "thg 8", "thg 9", "thg 10", "thg 11", "thg 12")
        } else {
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        }

    fun pendingTasks(count: Int): String = when {
        lang == AppLang.VI -> if (count == 1) "1 việc đang chờ" else "$count việc đang chờ"
        count == 1 -> "1 pending task"
        else -> "$count pending tasks"
    }

    fun pitchesLeft(count: Int): String = when {
        lang == AppLang.VI -> if (count == 1) "1 lời ngỏ còn" else "$count lời ngỏ còn"
        count == 1 -> "1 pitch left"
        else -> "$count pitches left"
    }

    fun needMoreChars(n: Int): String = s("At least $n more characters", "Còn ít nhất $n ký tự nữa")

    fun pitchedBackTo(name: String): String = s(
        "You pitched back $name! Check your inbox for updates.",
        "Bạn đã ngỏ lời với $name! Kiểm tra hộp thư để theo dõi.",
    )

    fun canceledBy(name: String): String = s("Canceled by $name", "Đã hủy bởi $name")

    fun closesAt(time: String): String = s("Closes $time", "Đóng lúc $time")

    fun opensAt(whenText: String): String = s("Opens $whenText", "Mở lúc $whenText")
}
