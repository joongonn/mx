'use strict';

angular.module('mx.filters', [])
  .filter('interpolate', ['version', function(version) {
    return function(text) {
      return String(text).replace(/\%VERSION\%/mg, version);
    };
  }])
  .filter('sentOn', ['$filter', function($filter) {
    return function(timeMs, format) {
      if (!timeMs) {
        return;
      }
      
      var nowMs = (new Date).getTime();
      var elapsed = Math.floor((nowMs - timeMs) / 1000);
      if (elapsed < 24 * 60 * 60) {
        var s;
        if (format == 'verbose') {
          s = $filter('date')(timeMs, 'h:mm a');
          if (elapsed < 60 * 60) {
            s = s + ' (' + Math.floor(elapsed / 60) + ' min ago)';
          } else {
            s = s + ' (' + Math.floor(elapsed / (60 * 60)) + ' hr ago)';
          }
        }
        else // short
        {
          if (elapsed < 60 * 60) {
            s = Math.floor(elapsed / 60) + ' min ago';
          } else {
            var currentHourOfDay = $filter('date')(nowMs, 'H');
            var hourOfDay = $filter('date')(timeMs, 'H');
            if (currentHourOfDay >= hourOfDay && elapsed < 24 * 60 * 60) {
              s = $filter('date')(timeMs, 'h:mm a');
            } else {
              s = $filter('date')(timeMs, 'MMM d');
            }
          }
        }
        return s;
      } else {
        return $filter('date')(timeMs, 'MMM d');
      }
    };
  }]);
  