/* global $, CodeMirror, alert */

$(function () {
  let editors = {}
  $('textarea.codemirror').each(function() {
    editors[$(this).closest('div.container').attr('id')] = CodeMirror.fromTextArea($(this).get(0), {
      mode: 'text/x-java',
      lineNumbers: true,
      matchBrackets: true
    })
  })

  let run = (id) => {
    let source = editors[id]
    let output = $(`#${ id }Output pre`)

    let toRun = source.getValue()
    if (toRun.trim() === "") {
      output.html(`&lt; Hit Control-Enter to run your Java code...`)
      return
    }
    let run
    source = source.getValue() + "\n"
    if (id === "runClass") {
      run = {
        as: "SimpleCompiler", class: "Example",
        sources: [ source ], runTests: true, testClassName: "SumTest",
      }
    } else {
      run = {
        as: "Snippet", source
      }
    }
    console.log(run)
    $.post("/run", JSON.stringify(run)).done(result => {
      console.log(result)
      if (result.executed) {
        output.text(result.output)
      } else if (result.timedOut) {
        output.html(`<span class="text-danger">Timeout</span>`)
      } else if (!result.checkstyleSucceeded) {
        output.html(`<span class="text-danger">Checkstyle error:\n${ result.checkstyleErrorMessage }</span>`)
      } else if (!result.compiled) {
        output.html(`<span class="text-danger">Compiler error:\n${ result.compilationErrorMessage }</span>`)
      } else if (!result.executed) {
        output.html(`<span class="text-danger">Runtime error:\n${ result.executionErrorMessage }</span>`)
      }
    }).fail((xhr, status, error) => {
      console.error("Request failed")
      console.error(JSON.stringify(xhr, null, 2))
      console.error(JSON.stringify(status, null, 2))
      console.error(JSON.stringify(error, null, 2))
    })
  }

  $(window).keypress(function (event) {
    if (!(event.which === 13 && event.ctrlKey) &&
        !(event.which === 10 && event.ctrlKey)) {
      return true
    }
    event.preventDefault()
    run($(event.target).closest('div.container').attr('id'))
  })
})
