var jenkinsRootUrl = null

function setRootURL(rootUrl) {
    jenkinsRootUrl = rootUrl;
}


function loadProjects() {
    var projectSelect = document.getElementById("projectNameId");
    var oldSelected = projectSelect.value;
    new Ajax.Request(jenkinsRootUrl + "/descriptor/com.synopsys.integration.coverity.post.CoverityPostBuildStep/fillProjectNameItems", {
        parameters: {projectName: oldSelected},
        onLoading: showLoadingProjects(),
        onComplete: function (t) {
            if (t.status == 200) {
                var json = t.responseText.evalJSON();

                projectSelect.options.length = 0;

                var selectedProject = "";
                json.values.each(function (project) {
                    var opt = document.createElement("option");
                    opt.value = project.value;
                    opt.text = project.name;
                    if (project.selected) {
                        selectedProject = project.value;
                    }
                    projectSelect.appendChild(opt);
                });
                projectSelect.value = selectedProject;
            }
            hideLoadingProjects();
        }
    });
}


function showLoadingProjects() {
    showLoading('projectNameId', 'projectsloading', 'Loading projects...');
}

function hideLoadingProjects() {
    hideLoading('projectNameId', 'projectsloading');
}

function showLoading(nameId, loadingId, loadingText) {
    var cell = document.getElementById(nameId).parentNode;
    if (cell) {
        var loadingDiv = cell.select("#" + loadingId).first();
        if (!loadingDiv) {
            var loadingDiv = document.createElement('div');
            loadingDiv.setAttribute('id', loadingId);
            loadingDiv.innerHTML = loadingText;
            loadingDiv.hide();
            cell.appendChild(loadingDiv);
        }

        function showLoading(ld) {
            ld.show();
        }

        showLoading.delay(0.2, loadingDiv);
    }
}

function hideLoading(nameId, loadingId) {
    var cell = document.getElementById(nameId).parentNode;
    var loadingDiv = cell.select("#" + loadingId).first();
    if (cell) {
        if (loadingDiv) {
            cell.removeChild(loadingDiv);
        }
    }
}
