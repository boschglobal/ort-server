/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import { useEffect, useState } from 'react';

import { calculateDuration } from '@/helpers/get-run-duration';

type RunDurationProps = {
  createdAt: string;
  finishedAt?: string;
  pollInterval: number;
};

export const RunDuration = ({
  createdAt,
  finishedAt,
  pollInterval,
}: RunDurationProps) => {
  const [currentTime, setCurrentTime] = useState<string>(
    new Date().toISOString()
  );

  useEffect(() => {
    if (!finishedAt) {
      const intervalId = setInterval(() => {
        setCurrentTime(new Date().toISOString());
      }, pollInterval); // Update every pollInterval

      // Cleanup interval on component unmount
      return () => clearInterval(intervalId);
    }
  }, [finishedAt, pollInterval]);

  return (
    <div>
      {finishedAt ? (
        calculateDuration(createdAt, finishedAt)
      ) : (
        <div className='animate-pulse italic'>
          {calculateDuration(createdAt, currentTime)}
        </div>
      )}
    </div>
  );
};