var jenkinsRootUrl = null

function setRootURL(rootUrl) {
    jenkinsRootUrl = rootUrl;
}

function loadProjects() {
    var projectSelect = document.getElementById('projectNameId');
    var oldProjectSelected = projectSelect.value;
    var fillURL = jenkinsRootUrl + "/descriptor/com.synopsys.integration.coverity.post.CoverityPostBuildStep/fillProjectNameItems";
    var requestParameters = {projectName: oldProjectSelected, updateNow: true};
    loadList('projectNameId', 'projectsLoading', 'Loading projects...', fillURL, requestParameters);
}

function loadStreams() {
    var projectSelect = document.getElementById('projectNameId');
    var oldProjectSelected = projectSelect.value;

    var streamSelect = document.getElementById('streamNameId');
    var oldStreamSelected = streamSelect.value;

    var fillURL = jenkinsRootUrl + "/descriptor/com.synopsys.integration.coverity.post.CoverityPostBuildStep/fillStreamNameItems";
    var requestParameters = {projectName: oldProjectSelected, streamName: oldStreamSelected, updateNow: true};
    loadList('streamNameId', 'streamsLoading', 'Loading streams...', fillURL, requestParameters);
}

function loadViews() {
    var viewSelect = document.getElementById('viewNameId');
    var oldViewSelected = viewSelect.value;

    var fillURL = jenkinsRootUrl + "/descriptor/com.synopsys.integration.coverity.post.CoverityPostBuildStep/fillViewNameItems";
    var requestParameters = {viewName: oldViewSelected, updateNow: true};
    loadList('viewNameId', 'viewsLoading', 'Loading views...', fillURL, requestParameters);
}

function loadList(selectId, loadingId, loadingText, fillURL, requestParameters) {
    var select = document.getElementById(selectId);
    new Ajax.Request(fillURL, {
        parameters: requestParameters,
        onLoading: showLoading(selectId, loadingId, loadingText),
        onComplete: function (t) {
            if (t.status == 200) {
                var json = t.responseText.evalJSON();

                select.options.length = 0;

                var selectedOption = "";
                json.values.each(function (currentOption) {
                    var opt = document.createElement("option");
                    opt.value = currentOption.value;
                    opt.text = currentOption.name;
                    if (currentOption.selected) {
                        selectedOption = currentOption.value;
                    }
                    select.appendChild(opt);
                });
                select.value = selectedOption;
            }
            hideLoading(selectId, loadingId);
        }
    });
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
