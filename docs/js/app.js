angular.module('defects4j-website', ['ngRoute', 'ui.bootstrap', 'anguFixedHeaderTable'])
	.config(function($routeProvider, $locationProvider) {
		$routeProvider
			.when('/bug/:project/:id', {
				controller: 'bugController'
			})
			.when('/', {
				controller: 'mainController'
			});
		// configure html5 to get links working on jsfiddle
		$locationProvider.html5Mode(false);
	})
	.directive('keypressEvents', [
		'$document',
		'$rootScope',
		function($document, $rootScope) {
			return {
				restrict: 'A',
				link: function() {
					$document.bind('keydown', function(e) {
						$rootScope.$broadcast('keypress', e);
						$rootScope.$broadcast('keypress:' + e.which, e);
					});
				}
			};
		}
	]).directive('diff', ['$http', function ($http) {
		return {
			restrict: 'A',
			link: function (scope, elem, attrs) {
				scope.$watch("$ctrl.bug.patch", function () {
					var diff2htmlUi = new Diff2HtmlUI({ diff: scope["$ctrl"].bug.patch });
					diff2htmlUi.draw($(elem), {inputFormat: 'java', showFiles: false, matching: 'lines'});
					diff2htmlUi.highlightCode($(elem));
				});
			}
		}
		}])
	.controller('welcomeController', function($uibModalInstance) {
		this.ok = function () {
			$uibModalInstance.close();
		};
	})
	.controller('bugModal', function($rootScope, $uibModalInstance, bug, classifications) {
		var $ctrl = this;
		$ctrl.bug = bug;
		$ctrl.classifications = classifications;

		$rootScope.$on('new_bug', function(e, bug) {
			$ctrl.bug = bug;
		});
		$ctrl.ok = function () {
			$uibModalInstance.close();
		};
		$ctrl.nextBug = function () {
			$rootScope.$emit('next_bug', 'next');
		};
		$ctrl.previousBug = function () {
			$rootScope.$emit('previous_bug', 'next');
		};
		$ctrl.actionName = function (key) {
			for(var i in $ctrl.classifications['Repair Actions']) {
				if ($ctrl.classifications['Repair Actions'][i][key] != null) {
					if ($ctrl.classifications['Repair Actions'][i][key].fullname) {
						return $ctrl.classifications['Repair Actions'][i][key].fullname;
					}
					return $ctrl.classifications['Repair Actions'][i][key].name;
				}
			}
			return null;
		};
		$ctrl.patternName = function (key) {
			for(var i in $ctrl.classifications['Repair Patterns']) {
				if ($ctrl.classifications['Repair Patterns'][i][key] != null) {
					if ($ctrl.classifications['Repair Patterns'][i][key].fullname) {
						return $ctrl.classifications['Repair Patterns'][i][key].fullname;
					}
					return $ctrl.classifications['Repair Patterns'][i][key].name;
				}
			}
			return null;
		};
		$ctrl.repairName = function (key) {
			var repairNames = $ctrl.classifications['Runtime Information']["Automatic Repair"];
			if (repairNames[key] != null) {
				if (repairNames[key].fullname) {
					return repairNames[key].fullname;
				}
				return repairNames[key].name;
			}
			return null;
		};
	})
	.controller('bugController', function($scope, $location, $rootScope, $routeParams, $uibModal) {
		var $ctrl = $scope;
		$ctrl.classifications = $scope.$parent.classifications;
		$ctrl.bugs = $scope.$parent.filteredBug;
		$ctrl.index = -1;
		$ctrl.bug = null;

		$scope.$watch("$parent.filteredBug", function () {
			$ctrl.bugs = $scope.$parent.filteredBug;
			$ctrl.index = getIndex($routeParams.project, $routeParams.id);
		});
		$scope.$watch("$parent.classifications", function () {
			$ctrl.classifications = $scope.$parent.classifications;
		});

		var getIndex = function (project, commit) {
			if ($ctrl.bugs == null) {
				return -1;
			}
			for (var i = 0; i < $ctrl.bugs.length; i++) {
				if ($ctrl.bugs[i].project == project && $ctrl.bugs[i].commit == commit) {
					return i;
				}
			}
			return -1;
		};

		$scope.$on('$routeChangeStart', function(next, current) {
			$ctrl.index = getIndex(current.params.project, current.params.id);
		});

		var modalInstance = null;
		$scope.$watch("index", function () {
			if ($scope.index != -1) {
				if (modalInstance == null) {
					modalInstance = $uibModal.open({
						animation: true,
						ariaLabelledBy: 'modal-title',
						ariaDescribedBy: 'modal-body',
						templateUrl: 'modelBug.html',
						controller: 'bugModal',
						controllerAs: '$ctrl',
						size: "lg",
						resolve: {
							bug: function () {
								return $scope.bugs[$scope.index];
							},
							classifications: $scope.classifications
						}
					});
					modalInstance.result.then(function () {
						modalInstance = null;
						$location.path("/");
					}, function () {
						modalInstance = null;
			$location.path("/");
					})
				} else {
					$rootScope.$emit('new_bug', $scope.bugs[$scope.index]);
				}
			}
		});
		var nextBug = function () {
	  var index  = $scope.index + 1;
	  if (index == $ctrl.bugs.length)  {
		index = 0;
	  }
	  $location.path( "/bug/" + $ctrl.bugs[index]["project"] + "/" + $ctrl.bugs[index]["commit"] );
			return false;
		};
		var previousBug = function () {
	  var index  = $scope.index - 1;
	  if (index < 0) {
		index = $ctrl.bugs.length - 1;
	  }
	  $location.path( "/bug/" + $ctrl.bugs[index]["project"] + "/" + $ctrl.bugs[index]["commit"] );
			return false;
		};

	$scope.$on('keypress:39', function () {
	  $scope.$apply(function () {
		nextBug();
	  });
	});
	$scope.$on('keypress:37', function () {
	  $scope.$apply(function () {
		previousBug();
	  });
	});
	$rootScope.$on('next_bug', nextBug);
	$rootScope.$on('previous_bug', previousBug);
	})
	.controller('mainController', function($scope, $location, $rootScope, $http, $uibModal) {
		$scope.sortType     = ['project', 'commit']; // set the default sort type
		$scope.sortReverse  = false;
		$scope.match  = "all";
		$scope.filter   = {};

		// create the list of sushi rolls 
		$scope.bugs = [];
		$scope.classifications = [];

		$http.get("data/classification.json").then(function (response) {
			$scope.classifications = response.data;
		});

		$http.get("data/bugs.json").then(function (response) {
			$scope.bugs = response.data;

			var exceptions = {};
			$scope.classifications["Runtime Information"]["Exceptions"] = exceptions;

			var element = angular.element(document.querySelector('#menu')); 
			var height = element[0].offsetHeight;

			angular.element(document.querySelector('#mainTable')).css('height', (height-160)+'px');
		});

		$scope.filterName = function (filterKey) {
			for (var j in $scope.classifications) {
				for(var i in $scope.classifications[j]) {
					if ($scope.classifications[j][i][filterKey] != null) {
						if ($scope.classifications[j][i][filterKey].fullname) {
							return $scope.classifications[j][i][filterKey].fullname;
						}
						return $scope.classifications[j][i][filterKey].name;
					}
				}
			}
			return filterKey;
		}

		$scope.openBug = function (bug) {
			$location.path( "/bug/" + bug["project"] + "/" + bug["commit"] );
		};

		$scope.sort = function (sort) {
			if (sort == $scope.sortType || (sort[0] == 'project' && $scope.sortType[0] == 'project')) {
				$scope.sortReverse = !$scope.sortReverse; 
			} else {
				$scope.sortType = sort;
				$scope.sortReverse = false; 
			}
			return false;
		}

		$scope.countBugs = function (key, filter) {
			if (filter.count) {
				return filter.count;
			}
			var count = 0;
			for(var i = 0; i < $scope.bugs.length; i++) {
				if ($scope.bugs[i][key] === true) {
					count++;
				}
			}
			filter.count = count;
			return count;
		};

		$scope.bugsFilter = function (value, index, array) {
			var allFalse = true;
			for (var i in $scope.filter) {
				if ($scope.filter[i] === true) {
					allFalse = false;
					break;
				}
			}
			if (allFalse) {
				return true;
			}

			for (var i in $scope.filter) {
				if ($scope.filter[i] === true) {
					if (value[i] === true) {
						if ($scope.match=="any") {
							return true;
						}
					} else if ($scope.match=="all"){
						return false;
					}
				}
			}
			if ($scope.match=="any") {
				return false;
			} else {
				return true;
			}
		};
	});