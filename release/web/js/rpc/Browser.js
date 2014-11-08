$(document).ready(function() {
   BROWSER = document.getElementById("BROWSER");
   var spaces = '&nbsp;&nbsp;&nbsp;&nbsp;';
   var html = '<h3>';

   // Retrieve file shares
   $.getJSON("/getBrowserShares", function(data) {
      $.each(data, function( i, share ) {
         html += '<a href="/' + share + '">' + share + '</a>' + spaces;
      });
      html += '</h3>';
      BROWSER.innerHTML = html;
   })
   .error(function(xhr, status) {
      util_handleError("/getBrowserShares", xhr, status);
   });
});