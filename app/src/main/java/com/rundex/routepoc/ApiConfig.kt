package com.rundex.routepoc

/**
 * 백엔드(Supabase) 연결 설정. 값이 채워지면 동기화가 활성화된다.
 * 비어 있으면 [ApiClient]는 모든 호출을 조용히 무시(로컬 기능만 동작).
 *
 * 예) BASE_URL = "https://<project-ref>.supabase.co"
 */
object ApiConfig {
    const val BASE_URL = "https://jtbtqmcwjtuzsfpqzunf.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp0YnRxbWN3anR1enNmcHF6dW5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEzMzI1MTQsImV4cCI6MjA5NjkwODUxNH0.w6-AUyifqYlcTkXRzLfX8TtQGwPEF-pu2dUiBT5rA94"

    val enabled: Boolean get() = BASE_URL.isNotBlank()
}
