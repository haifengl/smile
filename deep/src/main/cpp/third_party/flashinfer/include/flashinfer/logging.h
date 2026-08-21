/*
 * Copyright (c) 2025 by FlashInfer team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef FLASHINFER_LOGGING_H_
#define FLASHINFER_LOGGING_H_

#include <spdlog/sinks/stdout_color_sinks.h>
#include <spdlog/spdlog.h>

#define FLASHINFER_LOG_TRACE(...) spdlog::trace(__VA_ARGS__)
#define FLASHINFER_LOG_DEBUG(...) spdlog::debug(__VA_ARGS__)
#define FLASHINFER_LOG_INFO(...) spdlog::info(__VA_ARGS__)
#define FLASHINFER_LOG_WARN(...) spdlog::warn(__VA_ARGS__)
#define FLASHINFER_LOG_ERROR(...) spdlog::error(__VA_ARGS__)
#define FLASHINFER_LOG_CRITICAL(...) spdlog::critical(__VA_ARGS__)

namespace flashinfer {

namespace logging {

inline void set_log_level(spdlog::level::level_enum lvl) {
  auto fmt = "[%Y-%m-%d %H:%M:%S.%f] [%n] [%^%l%$] %v";
  auto console_sink = std::make_shared<spdlog::sinks::stdout_color_sink_mt>();
  console_sink->set_pattern(fmt);
  console_sink->set_level(lvl);
  spdlog::set_default_logger(std::make_shared<spdlog::logger>("flashinfer", console_sink));
  spdlog::set_level(lvl);
}

}  // namespace logging

}  // namespace flashinfer

#endif  // FLASHINFER_LOGGING_H_
