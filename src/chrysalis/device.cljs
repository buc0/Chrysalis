;; Chrysalis -- Kaleidoscope Command Center
;; Copyright (C) 2017  Gergely Nagy <algernon@madhouse-project.org>
;;
;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns chrysalis.device
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [chrysalis.hardware :as hardware]
            [chrysalis.core :as core :refer [state]]

            [cljs.core.async :refer [<!]]))

(defn switch-to! [d]
  (if d
    (swap! state assoc-in [:current-device :device] d)
    (swap! state assoc :current-device nil)))

(defn select-by-serial! [serial]
  (swap! state assoc-in [:current-device :device]
         (first (filter (fn [device]
                          (= (:serialNumber device) serial)) (:devices @state)))))

(defn current []
  (:current-device @state))

(defn detect! []
  (swap! state assoc :devices [])
  (switch-to! nil)

  (let [in (hardware/detect (hardware/scan))]
    (go-loop []
      (when-let [device (<! in)]
        (swap! state (fn [state device]
                       (update-in state [:devices] conj device)) device)
        (recur))))
  nil)


