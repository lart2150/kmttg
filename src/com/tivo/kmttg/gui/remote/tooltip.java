/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.remote;

import javafx.scene.control.Tooltip;

import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.util.debug;

public class tooltip {
   
   public static Tooltip getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivo_todo")) {
         text = "Select TiVo for which to retrieve To Do list.";
      }
      else if (component.equals("refresh_todo")){
         text = "<b>Refresh</b><br>";
         text += "Refresh To Do list of selected TiVo.";
      }
      else if (component.equals("cancel_todo")){
         text = "<b>Cancel</b><br>";
         text += "Cancel ToDo recordings selected in table below. As a shortcut you can also use the<br>";
         text += "<b>Delete</b> keyboard button to cancel selected shows in the table as well.";
      }
      else if (component.equals("modify_todo")){
         text = "<b>Modify</b><br>";
         text += "Modify recording options of selected show in table below.";
      }
      else if (component.equals("export_todo")){
         text = "<b>Export</b><br>";
         text += "Export selected TiVo ToDo list to a csv file which can be easily<br>";
         text += "imported into an Excel spreadsheet or equivalent.";
      }
      else if (component.equals("trim_todo")){
         text = "<b>Select Repeats</b><br>";
         text += "Search for and select table entries that are likely repeats.<br>";
         text += "Entries with same title and subtitle are treated as repeats.<br>";
         text += "Entries containing a subtitle and same programId are treated as repeats.<br>";
         text += "Use the <b>Cancel</b> button to unschedule selected entries.";
      }
      else if (component.equals("tivo_guide")) {
         text = "Select TiVo for which to retrieve guide listings.";
      }
      else if (component.equals("guide_start")) {
         text = "<b>Start</b><br>";
         text += "Select guide start time to use when obtaining listings.<br>";
         text += "NOTE: If you are inside a channel folder when you change this setting<br>";
         text += "the guide listings will automatically update to new date.";
      }
      else if (component.equals("guide_channels")) {
         text = "<b>All</b><br>";
         text += "If this option is checked then all channels in your lineup are shown, else just<br>";
         text += "channels that are enabled in your lineup are shown.";
      }
      else if (component.equals("refresh_guide")){
         text = "<b>Channels</b><br>";
         text += "Refresh list of channels for this TiVo. Note that only channels that are enabled in<br>";
         text += "your lineup are displayed unless you enable the <b>All</b> option to the left of this button.";
      }
      else if (component.equals("back_guide")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view..";
      }
      else if (component.equals("refresh_search_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("guide_record")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("tivo_stream")) {
         text = "Select TiVo for which to retrieve streaming entries.<br>";
         text += "NOTE: Only series 4 or later TiVos are supported.";
      }
      else if (component.equals("refresh_stream")) {
         text = "<b>Refresh</b><br>";
         text += "Refresh list of streaming items for selected TiVo.<br>";
         text += "The table will show individual My Shows streaming items such as Streaming Movie items<br>";
         text += "as well as any One Passes with streaming options enabled.<br>";
         text += "NOTE: When clicking on a folder it can take several seconds for episodes related to the<br>";
         text += "streaming One Pass to be obtained, so please be patient.";
      }
      else if (component.equals("remove_stream")) {
         text = "<b>Remove</b><br>";
         text += "Remove selected list of streaming items in table from this TiVo.<br>";
         text += "NOTE: Folder entries can also be selected and removed and is accomplished by unsubscribing<br>";
         text += "the related One Pass (Season Pass) entries.<br>";
         text += "Following any successful removal the table of streaming items is automatically refreshed.";
      }
      else if (component.equals("back_stream")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view..";
      }
      else if (component.equals("export_channels")){
         text = "<b>Export ...</b><br>";
         text += "Export current channel lineup of this TiVo to CSV file.<br>";
         text += "Spreadsheet includes both included and excluded channels from channel list.<br>";
         text += "This can be useful for a new TiVo to consult a spreadsheet so as to know which<br>";
         text += "channels to keep and remove.";
      }
      else if (component.equals("guide_recordSP")){
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("guide_manual_record")) {
         text = "<b>MR</b><br>";
         text += "Schedule a manual recording on selected TiVo. This can be a single manual recording or<br>";
         text += "a repeating manual recording just as can be created using TiVo GUI.";
      }
      else if (component.equals("guideChanList")) {
         text = "<b>Guide Channels</b><br>";
         text += "Select a channel in this list to obtain guide listings for with the given start time.<br>";
         text += "Listings will be shown in the table to the right for a 24 hour window.";
      }
      else if (component.equals("guide_refresh_todo")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "obtain channel list, but subsequent guide searches use that same ToDo list to highlight<br>";
         text += "programs that are scheduled to record already.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings while browsing guide entries.";
      }
      else if (component.equals("tivo_cancel")) {
         text = "Select TiVo for which to display list of shows that will not record.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.";
      }
      else if (component.equals("refresh_cancel_top")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo. Click on a folder in table below to see<br>";
         text += "all shows that will not record for reason matching the folder name.";
      }
      else if (component.equals("record_cancel")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("refresh_cancel_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("includeHistory_cancel")){
         text = "<b>Include History</b><br>";
         text += "Include past history prior to current time if enabled.";
      }
      else if (component.equals("includeFree")){
         text = "<b>Free streaming content</b><br>";
         text += "If enabled, include free streaming content in search.";
      }
      else if (component.equals("includePaid")){
         text = "<b>Paid streaming content</b><br>";
         text += "If enabled, include paid streaming content in search.";
      }
      else if (component.equals("includeVod")){
         text = "<b>VOD content</b><br>";
         text += "If enabled, include VOD content in search.";
      }
      else if (component.equals("unavailable")){
         text = "<b>Unavailable</b><br>";
         text += "If enabled, include content currently unavailable in guide data in search.";
      }
      else if (component.equals("explain_cancel")) {
         text = "<b>Explain</b><br>";
         text += "Obtains and shows conflict details in the message window for the selected show in the table.<br>";
         text += "NOTE: This is only works for shows under 'programSourceConflict' folder.";
      }
      else if (component.equals("refresh_todo_cancel")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "refresh the Will Not Record list, but subsequent browsing of results will use that same ToDo list<br>";
         text += "to highlight shows scheduled to record on other TiVos.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings since last refresh of Will Not Record list.";
      }
      else if (component.equals("autoresolve")) {
         text = "<b>Autoresolve</b><br>";
         text += "Search for all conflicts of type 'programSourceConflict' on all RPC or Mind enabled<br>";
         text += "TiVos and try and automatically schedule them to record on alternate TiVos.<br>";
         text += "NOTE: This operation can take a long time to complete. Progress is periodically<br>";
         text += "printed as 'AutomaticConflictsHandler' messages to the message window. This button<br>";
         text += "is disabled until operation completes to prevent running more than once at a time.<br>";
         text += "NOTE: You can run this operation in kmttg batch mode by starting kmttg with <b>-c</b> argument<br>";
         text += "i.e: <b>java -jar kmttg.jar -c</b>. That way you can setup a scheduler to run this<br>";
         text += "automatically without having to run it manually from the GUI.";
      }
      else if (component.equals("tivo_deleted")) {
         text = "Select TiVo for which to display list of deleted shows (in Recently Deleted state)";
      }
      else if (component.equals("refresh_deleted")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo.";
      }
      else if (component.equals("recover_deleted")){
         text = "<b>Recover</b><br>";
         text += "Recover from Recently Deleted selected individual show(s) in table on specified TiVo.";
      }
      else if (component.equals("permDelete_deleted")){
         text = "<b>Permanently Delete</b><br>";
         text += "Permanently delete selected individual show(s) in table on specified TiVo.<br>";
         text += "NOTE: Once deleted these shows are removed from Recently Deleted and can't be recovered.";
      }
      else if (component.equals("tivo_thumbs")) {
         text = "Select TiVo for which to display list of thumbed shows.";
      }
      else if (component.equals("refresh_thumbs")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo.";
      }
      else if (component.equals("update_thumbs")){
         text = "<b>Modify</b><br>";
         text += "Updates thumbs values on TiVo according to changed values in table.<br>";
         text += "NOTE: You can directly edit values in the RATING column of the table.<br>";
         text += "Valid settings for RATING are: -3,-2,-1,0,1,2,3. A value of 0 means no thumbs<br>";
         text += "and an update with 0 value will remove the thumbs for the respective show.";
      }
      else if (component.equals("copy_thumbs")){
         text = "<b>Copy</b><br>";
         text += "This is used to copy selected thumbs in the table to one of your TiVos.<br>";
         text += "Select 1 or more rows in the table that you want copied, then press this button to initiate<br>";
         text += "the copy. You will be prompted for destination TiVo to copy to (series 4 and later only).<br>";
         text += "If you want to copy from a previously saved thumbs list, then use the <b>Load</b> button to load<br>";
         text += "thumbs from a file, then use this <b>Copy</b> button to copy selected entries to a TiVo.<br>";
         text += "Note that any existing shows with ratings on the destination TiVo will be overriden.";
      }
      else if (component.equals("save_thumbs")){
         text = "<b>Save</b><br>";
         text += "Save the currently displayed thumbs list to a file. This file can then be loaded<br>";
         text += "at a later date into this table, then entries from the table can be copied to your TiVos<br>";
         text += "if desired by selecting entries in the table and clicking on <b>Copy</b> button.<br>";
         text += "i.e. This is a way to backup your thumb ratings.";
      }
      else if (component.equals("load_thumbs")){
         text = "<b>Load</b><br>";
         text += "Load a previously saved thumbs list from a file. When loaded the table will have a<br>";
         text += "<b>Loaded: </b> prefix in the TITLE column indicating that these were loaded from a file<br>";
         text += "to distinguish from normal case where they were obtained from displayed TiVo name.<br>";
         text += "Note that loaded thumbs can then be copied to TiVos by selecting rows of interest in<br>";
         text += "the table and then using the <b>Copy</b> button to copy them to a TiVo.";
      }
      else if (component.equals("tivo_channels")) {
         text = "Select TiVo for which to display list of channels.";
      }
      else if (component.equals("refresh_channels")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo.";
      }
      else if (component.equals("update_channels")){
         text = "<b>Modify</b><br>";
         text += "Update received channel list on TiVo according to RECEIVED column changes made to table.<br>";
         text += "NOTE: You can directly edit boolean values in the RECEIVED column of the table.";
      }
      else if (component.equals("copy_channels")){
         text = "<b>Copy</b><br>";
         text += "This is used to copy selected channels in the table to one of your TiVos.<br>";
         text += "Select 1 or more rows in the table that you want copied, then press this button to initiate<br>";
         text += "the copy. You will be prompted for destination TiVo to copy to.<br>";
         text += "If you want to copy from a previously saved channels list, then use the <b>Load</b> button to load<br>";
         text += "channels from a file, then use this <b>Copy</b> button to copy selected entries to a TiVo.";
      }
      else if (component.equals("save_channels")){
         text = "<b>Save</b><br>";
         text += "Save channels list for currently selected TiVo to a file. This file can then be loaded<br>";
         text += "at a later date into this table, then entries from the table can be copied to your TiVos<br>";
         text += "if desired by selecting entries in the table and clicking on <b>Copy</b> button.<br>";
         text += "i.e. This is a way to backup your received channels list.";
      }
      else if (component.equals("load_channels")){
         text = "<b>Load</b><br>";
         text += "Load a previously saved channels list from a file. When loaded the table will have a<br>";
         text += "<b>Loaded: </b> prefix in the TITLE column indicating that these were loaded from a file<br>";
         text += "to distinguish from normal case where they were obtained from displayed TiVo name.<br>";
         text += "Note that loaded channels can then be copied to TiVos by selecting rows of interest in<br>";
         text += "the table and then using the <b>Copy</b> button to copy them to a TiVo.";         
      }
      else if (component.equals("tivo_search")) {
         text = "Select TiVo for which to perform search with.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.";
      }
      else if (component.equals("search_type")) {
         text = "Select type of search to perform:<br>";
         text += "<b>keywords</b> => traditional keyword search in show titles, subtitles, and descriptions<br>";
         text += "For other role choices you should provide person name in the search field. See the tooltip for<br>";
         text += "the search field for details on the expected syntax of person names.";
      }
      else if (component.equals("button_search")) {
         text = "<b>Search</b><br>";
         text += "Start a search of specified keywords.";
      }
      else if (component.equals("text_search")) {
         text = "Specify keywords to search for. NOTE: For <b>Type=keywords</b>, multiple words mean logical AND operation.<br>";
         text += "To shorten search times include more words in the search. Very generic/short<br>";
         text += "keywords will lead to much longer search times.<br>";
         text += "For <b>Type=role type</b> searches use the following syntax:<br>";
         text += "<b>FirstName LastName</b>. EXAMPLE: clint eastwood<br>";
         text += "<b>LastName</b>. EXAMPLE: eastwood. (When only 1 string provided it's assumed to be last name)<br>";
         text += "To search for more than 1 person at a time (OR operation), separate each by a comma. For example:<br>";
         text += "<b>clint eastwood, tommy jones</b>";
      }
      else if (component.equals("adv_search")) {
         text = "Brings up the <b>Advanced Search</b> dialog window which has more advanced search criteria<br>";
         text += "and allows you to create and save searches much like creating wishlists on a TiVo.<br>";
         text += "The results of advanced searches are displayed in the Search table in this tab.";
      }
      else if (component.equals("max_search")) {
         text = "<b>Max</b><br>";
         text += "Specify maximum number of hits to limit search to.<br>";
         text += "Depending on keywords the higher you set this limit the longer the search will take.<br>";
         text += "<b>NOTE: Use the scrollbar to change this number, or you can type in a number and press 'Enter'</b>";
      }
      else if (component.equals("record_search")) {
         text = "<b>Record</b><br>";
         text += "Schedule a one time recording of show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "<b>NOTE: For streaming only titles this will add a bookmark of the title to the TiVo's My Shows.</b><br>";
         text += "<b>Streaming titles can be movies, episodes or even currently unavailable content.</b><br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("record_sp_search")) {
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("wishlist_search")) {
         text = "<b>Create Wishlist</b><br>";
         text += "Create a wishlist on selected TiVo. If a show is selected in table then the title will be set<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "automatically to that title in the wishlist dialog that comes up.<br>";
         text += "You are prompted with wishlist dialog where you can define wishlist with boolean logic<br>";
         text += "for keywords, title keywords, actors and directors.<br>";
         text += "NOTE: Even though a title is required to be specified, the Wishlist will be named by<br>";
         text += "TiVo according to the search elements you setup.<br>";
         text += "NOTE: Existing non-autorecord wishlists are not visible or editable via RPC and have to<br>";
         text += "be managed on the TiVo itself.";
      }
      else if (component.equals("refresh_todo_search")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "perform the first search, but subsequent searches will use that same ToDo list to highlight<br>";
         text += "programs in search results that are scheduled to record already.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings since running the first search.";
      }
      else if (component.equals("refresh_search_top")){
         text = "<b>Refresh</b><br>";
         text += "Click on a folder in table below to show related search matches.";
      }
      else if (component.equals("refresh_search_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("tivo_sp")) {
         text = "Select TiVo for which to retrieve Season Passes list.";
      }
      else if (component.equals("tivo_rc")) {
         text = "Select which TiVo you want to control.<br>";
         text += "NOTE: This will use RPC or telnet protocol to communicate with your TiVo(s),<br>";
         text += "so make sure network remote setting on your TiVo is enabled.";
      }
      else if (component.equals("tivo_premiere")) {
         text = "Select TiVo to use for finding shows that are Season or Series premieres.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.";
      }
      else if (component.equals("refresh_premiere")){
         text = "<b>Search</b><br>";
         text += "Find season & series premieres on all the channels selected in the<br>";
         text += "channels list. This saves currently selected channel list to file as well.<br>";
         text += "<b>NOTE: TiVo guide data does not have episode number information for some shows,</b><br>";
         text += "<b>and hence those shows cannot be identified as premieres.</b>.";
      }
      else if (component.equals("premiere_channels_update")){
         text = "<b>Update Channels</b><br>";
         text += "Use this button to obtain list of channels received from selected TiVo.<br>";
         text += "Once you have the list then you can select which channels to include in the<br>";
         text += "search for season & series premieres (in list to right of the table below).";
      }
      else if (component.equals("premiere_channels")) {
         text = "Select which channels you want to include in the search for Season & Series<br>";
         text += "premieres. NOTE: The more channels you include the longer the search will take.<br>";
         text += "Use shift and left mouse button to select a range of channels or control + left<br>";
         text += "mouse button to add individual channels to selected set.";
      }
      else if (component.equals("premiere_days")) {
         text = "Select number of days you want to search for Season & Series premieres.";
      }
      else if (component.equals("record_premiere")){
         text = "<b>Record</b><br>";
         text += "Schedule individual recording for items selected in the table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("recordSP_premiere")){
         text = "<b>Season Pass</b><br>";
         text += "Schedule season passes for shows selected in the table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("refresh_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Season Pass list of selected TiVo. Note that by selecting 1 or more rows in the<br>";
         text += "table and using keyboard <b>Delete</b> button you can unsubscribe Season Passes.";
      }
      else if (component.equals("cancel_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Not Record list of selected TiVo. Click on folder in table to see all<br>";
         text += "entries associated with it.";
      }
      else if (component.equals("save_sp")){
         text = "<b>Save</b><br>";
         text += "Save the currently displayed Season Pass list to a file. This file can then be loaded<br>";
         text += "at a later date into this table, then entries from the table can be copied to your TiVos<br>";
         text += "if desired by selecting entries in the table and clicking on <b>Copy</b> button.<br>";
         text += "i.e. This is a way to backup your season passes.";
      }
      else if (component.equals("load_sp")){
         text = "<b>Load</b><br>";
         text += "Load a previously saved Season Pass list from a file. When loaded the table will have a<br>";
         text += "<b>Loaded: </b> prefix in the TITLE column indicating that these were loaded from a file<br>";
         text += "to distinguish from normal case where they were obtained from displayed TiVo name.<br>";
         text += "Note that loaded season passes can then be copied to TiVos by selecting rows of interest in<br>";
         text += "the table and then using the <b>Copy</b> button to copy them to a TiVo.";
      }
      else if (component.equals("export_sp")){
         text = "<b>Export</b><br>";
         text += "Export the currently displayed Season Pass list to a csv file which can be easily<br>";
         text += "imported into an Excel spreadsheet or equivalent.<br>";
         text += "<b>NOTE: This is NOT for saving/backing up season passes, use the Save button for that.</b>";
      }
      else if (component.equals("delete_sp")){
         text = "<b>Delete</b><br>";
         text += "This is used to remove a season pass currently selected in the table from one of your TiVos.<br>";
         text += "Select the Season Pass entry in the table and then click on this button to remove it.<br>";
         text += "This will cancel the Season Pass on the TiVo as well as remove the entry from the table.<br>";
         text += "NOTE: You can also use the keyboard <b>Delete</b> button instead if you wish.";
      }
      else if (component.equals("copy_sp")){
         text = "<b>Copy</b><br>";
         text += "This is used to copy selected season passes in the table to one of your TiVos.<br>";
         text += "Select 1 or more rows in the table that you want copied, then press this button to initiate<br>";
         text += "the copy. You will be prompted for destination TiVo to copy to.<br>";
         text += "If you want to copy from a previously saved SP list, then use the <b>Load</b> button to load<br>";
         text += "season passes from a file, then use this <b>Copy</b> button to copy selected entries to a TiVo.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo.";
      }
      else if (component.equals("modify_sp")){
         text = "<b>Modify</b><br>";
         text += "This is used to modify a season passes selected in the Season Pass table.<br>";
         text += "Select the season pass you want to modify in the table and then press this button to bring<br>";
         text += "up a dialog with season pass options that can be modified.";
      }
      else if (component.equals("reorder_sp")){
         text = "<b>Re-order</b><br>";
         text += "This is used to change priority order of season passes on selected TiVo to match the current<br>";
         text += "order displayed in the table. In order to change row order in the table you can use the mouse<br>";
         text += "to drag and drop rows to new locations. You can also select a row in the table and use the keyboard<br>";
         text += "<b>Up</b> and <b>Down</b> keys to move the row up and down. Once you are happy with priority order<br>";
         text += "displayed in the table use this button to have kmttg change the priority order on your TiVo.";
      }
      else if (component.equals("upcoming_sp")){
         text = "<b>Upcoming</b><br>";
         text += "Retrieve and show upcoming episodes of selected Season Pass entry in the table in the ToDo tab.<br>";
         text += "NOTE: Season pass titles with upcoming shows are displayed with (#) after the title indicating the<br>";
         text += "number of upcoming recordings. Titles without the (#) at the end have no upcoming recordings.";
      }
      else if (component.equals("conflicts_sp")){
         text = "<b>Conflicts</b><br>";
         text += "Retrieve and show conflicting episodes that won't record for selected Season Pass.<br>";
         text += "Any found entries will be displayed in the Won't Record table.<br>";
         text += "NOTE: Season pass entries with conflicting shows are displayed with a darker background color.";
      }
      else if (component.equals("tivo_rnpl")) {
         text = "Select TiVo for which to retrieve My Shows list.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("refresh_rnpl")){
         text = "<b>Refresh</b><br>";
         text += "Refresh My Shows list of selected TiVo.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("tab_rnpl")) {
         text = "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      if (component.equals("tivo_web")) {
         text = "Select TiVo for which to execute given URL.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("type_web")) {
         text = "Execute provided URL as given type.<br>";
         text += "If type specified as html, send given URL to TiVo internal web browser.<br>";
         text += "If type specified as flash, send given URL to TiVo internal flash player.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("send_web")) {
         text = "<b>Execute</b><br>";
         text += "If type specified as html, send given URL to TiVo internal web browser.<br>";
         text += "If type specified as flash, send given URL to TiVo internal flash player.<br>";
         text += "WEB NAVIGATION: Use kmttg Remote keys <b>Q, A, W, S</b> to navigate the page and <b>Select</b> button to<br>";
         text += "select or execute currently highlighted item on the page.<br>";
         text += "TYPING TEXT: You can use the kmttg <b>Remote</b> tab if there are fields where you<br>";
         text += "need to enter some text since the kmttg remote understands key presses for the basic<br>";
         text += "keyboard keys.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("url_web")) {
         text = "<b>URL</b><br>";
         text += "URL to use. Press Return in this field to send the provided URL to selected TiVo<br>";
         text += "and to add the entered URL to bookmarks below.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("tivo_info")) {
         text = "Select TiVo for which to retrieve system information.";
      }
      if (component.equals("bookmark_web")) {
         text = "<b>Bookmark</b><br>";
         text += "Select a previously entered URL & type in this list to set as current URL & type.";
      }
      if (component.equals("remove_bookmark")) {
         text = "<b>Remove Bookmark</b><br>";
         text += "Remove currently selected bookmark from the list.";
      }
      else if (component.equals("refresh_info")){
         text = "<b>Refresh</b><br>";
         text += "Retrieve system information for selected TiVo.";
      }
      else if (component.equals("netconnect_info")){
         text = "<b>Network Connect</b><br>";
         text += "Start a Network Connection (call home) for selected TiVo.";
      }
      else if (component.equals("connectStatus")){
         text = "<b>Connection Status</b><br>";
         text += "Print Network Connection (call home) status for selected TiVo.";
      }
      else if (component.equals("reboot_info")){
         text = "<b>Reboot</b><br>";
         text += "Reboot selected TiVo. This button is a macro that directs selected DVR to<br>";
         text += "the soft reboot screen and sends the 3x thumbs down + enter sequence to<br>";
         text += "reboot it - just as if you were doing so from the Help menu on DVR itself.<br>";
         text += "A confirmation prompt is used to prevent accidental use.<br>";
         text += "NOTE: This only works for series 4 or later TiVos";
      }
      else if (component.equals("jumpto_text")) {
         text = "<b>Jump to minute (Alt m)</b><br>";
         text += "Set playback position to exactly this number of minutes into the show.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("jumpahead_text")) {
         text = "<b>Skip minutes ahead (Alt .)</b><br>";
         text += "Set playback position this number of minutes ahead of current position.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("jumpback_text")) {
         text = "<b>Skip minutes back (Alt ,)</b><br>";
         text += "Set playback position this number of minutes behind current position.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("hme_button")) {
         text = "<b>Launch App</b><br>";
         text += "Launch the selected application to right of this button on the selected TiVo.";
      }
      else if (component.equals("hme_rc")) {
         text = "Select which application you want to launch on the selected TiVo.";
      }
      else if (component.equals("rc_sps_button")) {
         text = "<b>SPS backdoor</b><br>";
         text += "Execute the selected SPS backdoor on the selected TiVo.";
      }
      else if (component.equals("channelUp")) {
         text = "pg up";
      }
      else if (component.equals("channelDown")) {
         text = "pg down";
      }
      else if (component.equals("left")) {
         text = "left arrow";
      }
      else if (component.equals("zoom")) {
         text = "Alt z";
      }
      else if (component.equals("tivo")) {
         text = "Alt t";
      }
      else if (component.equals("up")) {
         text = "up arrow";
      }
      else if (component.equals("select")) {
         text = "Alt s";
      }
      else if (component.equals("down")) {
         text = "down arrow";
      }
      else if (component.equals("liveTv")) {
         text = "Alt l";
      }
      else if (component.equals("info")) {
         text = "Alt i";
      }
      else if (component.equals("right")) {
         text = "right arrow";
      }
      else if (component.equals("guide")) {
         text = "Alt g";
      }
      else if (component.equals("num1")) {
         text = "1";
      }
      else if (component.equals("num2")) {
         text = "2";
      }
      else if (component.equals("num3")) {
         text = "3";
      }
      else if (component.equals("num4")) {
         text = "4";
      }
      else if (component.equals("num5")) {
         text = "5";
      }
      else if (component.equals("num6")) {
         text = "6";
      }
      else if (component.equals("num7")) {
         text = "7";
      }
      else if (component.equals("num8")) {
         text = "8";
      }
      else if (component.equals("num9")) {
         text = "9";
      }
      else if (component.equals("clear")) {
         text = "delete";
      }
      else if (component.equals("num0")) {
         text = "0";
      }
      else if (component.equals("enter")) {
         text = "enter";
      }
      else if (component.equals("actionA")) {
         text = "Alt a";
      }
      else if (component.equals("actionB")) {
         text = "Alt b";
      }
      else if (component.equals("actionC")) {
         text = "Alt c";
      }
      else if (component.equals("actionD")) {
         text = "Alt d";
      }
      else if (component.equals("thumbsDown")) {
         text = "KP -";
      }
      else if (component.equals("reverse")) {
         text = "Alt <--";
      }
      else if (component.equals("back")) {
         text = "Alt k";
      }
      else if (component.equals("replay")) {
         text = "Alt 9";
      }
      else if (component.equals("play")) {
         text = "Alt ]";
      }
      else if (component.equals("pause")) {
         text = "Alt [";
      }
      else if (component.equals("slow")) {
         text = "Alt \\";
      }
      else if (component.equals("record")) {
         text = "Alt r";
      }
      else if (component.equals("thumbsUp")) {
         text = "KP +";
      }
      else if (component.equals("forward")) {
         text = "Alt -->";
      }
      else if (component.equals("advance")) {
         text = "Alt 0";
      }      
      else if (component.equals("standby")){
         text = "<b>Toggle standby</b><br>";
         text += "Toggle standby mode. In off mode audio/video outputs are disabled on the TiVo<br>";
         text += "and possible recording interruptions by Emergency Alert System (EAS) are avoided.";
      }
      else if (component.equals("toggle_cc")){
         text = "<b>Toggle CC</b><br>";
         text += "Toggle closed caption display.<br>";
         text += "NOTE: Assumes initial state of off.";
      }
      else if (component.equals("My Shows")){
         text = "<b>My Shows</b><br>";
         text += "My Shows (AKA Now Playing List).";
      }
      else if (component.equals("Find remote")){
         text = "<b>Find remote</b><br>";
         text += "Make remote control play tune (for supported TiVo remote models only).<br>";
         text += "NOTE: This uses telnet protocol only since there is no RPC equivalent.";
      }
      else if (component.contains("SPS")) {
         text = util.SPS.get(component + "_tooltip");
      }
      
      return MyTooltip.make(text);
   }

}
