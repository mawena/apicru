<?php

namespace Mawena\Apicru\Traits;

use PhpOffice\PhpWord\IOFactory;
use Dompdf\Dompdf;
use Dompdf\Options;
use HTMLPurifier;
use HTMLPurifier_Config;

trait WordToolsTrait
{
    function wordToPdf($wordFilePath, $pdfFilePath)
    {
        // Load the Word document
        $phpWord = IOFactory::load($wordFilePath);

        // Create a temporary file to store the HTML content
        $tempHtmlFile = tempnam(sys_get_temp_dir(), 'phpword_') . '.html';

        // Save PhpWord object as HTML
        $xmlWriter = IOFactory::createWriter($phpWord, 'HTML');
        $xmlWriter->save($tempHtmlFile);

        // Read the HTML content
        $html = file_get_contents($tempHtmlFile);

        // Clean and optimize HTML using HTML Purifier
        $config = HTMLPurifier_Config::createDefault();
        $purifier = new HTMLPurifier($config);
        $cleanHtml = $purifier->purify($html);

        // Initialize Dompdf
        $options = new Options();
        $options->set('isHtml5ParserEnabled', true);
        $dompdf = new Dompdf($options);

        // Load HTML into Dompdf
        $dompdf->loadHtml($cleanHtml);
        $dompdf->setPaper('A4', 'portrait');
        $dompdf->render();

        // Save PDF to file
        file_put_contents($pdfFilePath, $dompdf->output());

        // Delete temporary HTML file
        unlink($tempHtmlFile);
    }
}
