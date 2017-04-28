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

(ns chrysalis.plugin.page.repl.core
  (:require [chrysalis.core :refer [state pages]]
            [chrysalis.hardware :as hardware]
            [chrysalis.device :as device]
            [chrysalis.command :as command]
            [chrysalis.ui :refer [page]]))

(defn- send-command! [req]
  (let [[command & args] (.split req #" +")
        full-args (vec (cons (keyword command) (map (fn [arg]
                                                      (if (= (first arg) ":")
                                                        (.substring arg 1)
                                                        arg))
                                                    args)))
        result (apply command/run (:port (device/current)) full-args)]
    (swap! state update-in [:repl :history] (fn [s x]
                                              (cons x s))
           {:command (keyword command)
            :request req
            :result result
            :device (:device (device/current))})))

(defn repl-wrap [req index device result]
  (let [latest? (= index (count (get-in @state [:repl :history])))]
    [:div.row.chrysalis-page-repl-box {:key (str "repl-history-" index)}
     [:div.col-sm-12
      [:div.card {:class (when latest? "card-outline-success")}
       [:div.card-block
        [:div.card-title.row
         [:div.col-sm-6.text-left
          [:a.chrysalis-link-button.chrysalis-page-repl-collapse-toggle
           {:href (str "#repl-history-collapse-" index)
            :data-toggle :collapse}
           [:i.fa.fa-angle-down]]
          " " [:code req]]
         [:div.col-sm-6.text-right
          [:a.chrysalis-link-button {:on-click (fn [e]
                                                 (.preventDefault e)
                                                 (swap! state assoc-in [:repl :command] req)
                                                 (.focus (js/document.getElementById "repl-prompt-input")))}
           [:i.fa.fa-repeat]]]]
        [:div.collapse.show {:id (str "repl-history-collapse-" index)}
         (if-not (= result [:pre "\"\""])
           result
           [:pre [:i "<no output>"]])
         [:div.text-muted.text-right
          (get-in device [:meta :name])]]]]]]))

(defn- <command> [cmd]
  [:button.btn.btn-outline-primary
   {:type :button
    :key (str "repl-avail-command-modal-" cmd)
    :data-dismiss :modal
    :on-click (fn [e]
                (swap! state assoc-in [:repl :command] (name cmd))
                (.focus (js/document.getElementById "repl-prompt-input")))}
   cmd])

(defmulti display
  (fn [command _ _ _ _]
    command))

(defmethod display :default [_ req result device index]
  (when result
    (repl-wrap req index device
               [:pre (.stringify js/JSON (clj->js result) nil 2)])))

(defmethod display :help [_ req result device index]
  (when result
    (repl-wrap req index device
               [:div.list-group.col-sm-2
                (doall (map <command> (sort result)))])))

(defn <available-commands> []
  (when-let [port (:port (device/current))]
    (let [commands (command/run port :help)]
      (fn []
        [:div.modal.fade {:id "repl-available-commands"}
         [:div.modal-dialog
          [:div.modal-content
           [:div.modal-header
            [:h5.modal-title "Available commands"]]
           [:div.modal-body
            [:div.list-group
             (doall (map <command> (sort @commands)))]]
           [:div.modal-footer
            [:button.btn.btn-secondary
             {:type :button
              :data-dismiss :modal} "Cancel"]]]]]))))

(defmethod page [:render :repl] [_ _]
  [:div.container-fluid
   [<available-commands>]
   [:div.row.justify-content-left.chrysalis-page-repl-prompt
    [:form.col-sm-12 {:on-submit (fn [e]
                                   (.preventDefault e)
                                   (send-command! (get-in @state [:repl :command]))
                                   (swap! state assoc-in [:repl :command] nil))}
     [:div.input-group
      [:span.input-group-addon
       [:i.fa.fa-angle-right]]
      [:input.form-control {:id "repl-prompt-input"
                            :type :text
                            :placeholder "Type command here"
                            :autoFocus true
                            :value (get-in @state [:repl :command])
                            :on-change (fn [e]
                                         (swap! state assoc-in [:repl :command] (.-value (.-target e))))}]
      [:span.input-group-addon
       [:input.nav-link {:type :submit
                         :value ""
                         :title "Run!"}]]
      [:div.dropdown.input-group-addon
       [:a.dropdown-toggle.chrysalis-link-button {:href "#"
                                                  :data-toggle :dropdown}
        [:i.fa.fa-cogs]]
       [:div.dropdown-menu.text-right
        [:a.chrysalis-link-button.dropdown-item {:href "#"
                                                 :data-toggle :modal
                                                 :data-target "#repl-available-commands"
                                                 :title "List of available commands"}
         "Help"]
        [:hr]
        [:a.chrysalis-link-button.dropdown-item {:href "#"
                                                 :title "Clear the REPL history"
                                                 :on-click (fn [e]
                                                             (.preventDefault e)
                                                             (swap! state assoc-in [:repl :history] []))}
         "Clear history"]]]]]]
   (doall (map (fn [item index]
                 (display (:command item) (:request item) @(:result item) (:device item) index))
               (get-in @state [:repl :history])
               (range (count (get-in @state [:repl :history])) 0 -1)))])

(defmethod page [:enter :repl] [_ _]
  (swap! state assoc-in [:current-device :port]
         (hardware/open (get-in (device/current) [:device :comName]))))

(defmethod page [:leave :repl] [_ _]
  (.close (get-in @state [:current-device :port]))
  (swap! state assoc-in [:current-device :port] nil))

(swap! pages assoc :repl {:name "REPL"
                          :index 99
                          :disable? (fn [] (nil? (device/current)))})
(swap! state assoc :repl {:history []})
