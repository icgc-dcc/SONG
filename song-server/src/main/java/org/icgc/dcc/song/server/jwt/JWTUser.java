/*
 * Copyright (c) 2017. The Ontario Institute for Cancer Research. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.icgc.dcc.song.server.jwt;

import lombok.Data;

import java.util.List;

@Data
public class JWTUser {

  private String name;
  private String firstName;
  private String lastName;
  private String email;
  private String status;
  private String createdAt;
  private String lastLogin;
  private String preferredLanguage;
  private List<String> roles;

}