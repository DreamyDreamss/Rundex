package com.rundex.routepoc

/**
 * 백엔드(Supabase) 연결 설정. 값이 채워지면 동기화가 활성화된다.
 * 비어 있으면 [ApiClient]는 모든 호출을 조용히 무시(로컬 기능만 동작).
 *
 * 예) BASE_URL = "https://<project-ref>.supabase.co"
 */
object ApiConfig {
    const val BASE_URL = ""
    const val ANON_KEY = ""

    val enabled: Boolean get() = BASE_URL.isNotBlank()
}
